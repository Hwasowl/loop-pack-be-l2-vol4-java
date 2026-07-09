package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.DlqPublisher;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.order.OrderEventMessage;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 아웃박스 릴레이 — 미발행 outbox 행을 폴링해 order-events로 발행하고 published로 표시한다.
 * 발행(Kafka I/O)은 트랜잭션 밖에서 하고, 표시만 행별 짧은 트랜잭션(markPublished)으로 처리한다.
 * 발행 후 표시 전에 죽으면 다음 폴링에서 재발행(at-least-once) — 소비 핸들러가 멱등이라 안전.
 * ⚠️ 멀티 인스턴스에선 단일 실행 보장(분산락)이 필요하다. 현재는 단일 실행 전제(PaymentRecoveryScheduler와 동일).
 */
@Slf4j
@ConditionalOnProperty(name = "payment.order-consumer", havingValue = "kafka", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_SEND_RETRY = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DlqPublisher dlqPublisher;

    /**
     * 즉시발행(AFTER_COMMIT)과의 경합 회피용 유예. 이 시간이 지난 미발행 행만 폴링이 줍는다.
     * 즉시발행 타임아웃(5s)보다 넉넉히 크되, 유실 불허 경로의 복구 지연을 줄이도록 분 단위가 아닌 초 단위로 둔다.
     */
    @Value("${outbox.relay.backstop-grace-ms:60000}")
    private long backstopGraceMs;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    public void relay() {
        // 즉시발행이 처리할 시간을 준 뒤에도 미발행인 낙오분만 줍는다(즉시발행과의 이중발행 방지).
        ZonedDateTime threshold = ZonedDateTime.now().minus(Duration.ofMillis(backstopGraceMs));
        List<OutboxEvent> batch = outboxRepository.findUnpublishedOlderThan(threshold, BATCH_SIZE);
        for (OutboxEvent event : batch) {
            OrderEventMessage message;
            try {
                message = objectMapper.readValue(event.getPayload(), OrderEventMessage.class);
            } catch (Exception e) {
                // 역직렬화 실패(포이즌)는 재시도해도 영영 실패 → DLQ로 격리하고 FAILED 표시(재폴링 제외).
                // 단, DLQ 격리가 실제로 성공했을 때만 FAILED로 굳힌다 — 브로커 다운으로 격리도 실패하면
                // FAILED로 종결하는 순간 유실이므로, PENDING으로 두고 이번 배치는 중단해 다음 주기에 재시도한다.
                log.error("outbox 역직렬화 실패 — DLQ 격리 시도 (id={}, orderId={})", event.getId(), event.getAggregateId(), e);
                if (!dlqPublisher.publishSync(KafkaTopics.ORDER_EVENTS, event.getAggregateId().toString(), event.getPayload(), e)) {
                    break;
                }
                outboxRepository.markFailed(event.getId());
                continue;
            }
            try {
                // 브로커 ack까지 대기 — 발행 성공을 확인한 뒤에만 published로 표시한다.
                // 무한 대기 방지 — 브로커가 응답 없으면 스케줄러 스레드가 묶이지 않도록 타임아웃을 둔다.
                kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, event.getAggregateId().toString(), message).get(5, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.getId());
            } catch (Exception e) {
                event.recordSendFailure();
                outboxRepository.incrementRetryCount(event.getId());
                if (event.sendFailureExceeded(MAX_SEND_RETRY)) {
                    // send가 임계까지 반복 실패 → DLQ로 격리하고 FAILED 처리해 뒤 이벤트가 막히지 않게 한다.
                    // 단, 격리가 실제로 성공했을 때만 FAILED로 굳힌다 — 브로커 다운이 원인이면 DLQ(같은 브로커)도
                    // 실패하므로, 그땐 FAILED로 종결(=유실)하지 않고 PENDING으로 남겨 브로커 복구 후 재격리한다.
                    log.error("outbox 발행 {}회 초과 — DLQ 격리 시도 (id={}, orderId={})", event.getRetryCount(), event.getId(), event.getAggregateId(), e);
                    if (!dlqPublisher.publishSync(KafkaTopics.ORDER_EVENTS, event.getAggregateId().toString(), event.getPayload(), e)) {
                        break;
                    }
                    outboxRepository.markFailed(event.getId());
                    continue;
                }
                // 일시적 발행 실패(브로커 다운 등) → 표시 안 함. 순서 보존 위해 이번 배치는 중단, 다음 폴링에서 재시도.
                log.warn("outbox 발행 실패 (id={}, orderId={}, 재시도={}): {}", event.getId(), event.getAggregateId(), event.getRetryCount(), e.getMessage());
                break;
            }
        }
    }
}
