package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.order.OrderEventMessage;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.payment.PaymentCompleted;
import com.loopers.domain.payment.PaymentFailed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.TimeUnit;

/**
 * 커밋 확정 후(AFTER_COMMIT) 해당 주문의 미발행 아웃박스 행을 즉시 Kafka로 발행한다(하이브리드의 즉시성 담당).
 * 이건 best-effort 가속기다 — 실패하면 아무것도 하지 않고 릴레이 백스톱(재시도·임계 DLQ)에 맡긴다.
 * 그래서 여기엔 재시도·DLQ 격리 로직을 두지 않는다(중복 방지: 릴레이가 grace 지난 낙오분만 줍는다).
 * @Async라 커밋 스레드를 막지 않으며, 배포 시 유실은 graceful shutdown(spring.task.execution.shutdown)이 완화한다.
 * order-consumer=kafka일 때만 활성(테스트 in-process 프로파일에선 릴레이와 함께 꺼진다).
 */
@Slf4j
@ConditionalOnProperty(name = "payment.order-consumer", havingValue = "kafka", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class OutboxImmediatePublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentCompleted event) {
        publishNow(event.orderId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentFailed event) {
        publishNow(event.orderId());
    }

    private void publishNow(Long orderId) {
        for (OutboxEvent event : outboxRepository.findUnpublishedByAggregateId(orderId)) {
            try {
                OrderEventMessage message = objectMapper.readValue(event.getPayload(), OrderEventMessage.class);
                kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, event.getAggregateId().toString(), message)
                        .get(5, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("즉시 발행 중단 (id={}, orderId={}) — 릴레이 백스톱이 처리", event.getId(), orderId);
                return;
            } catch (Exception e) {
                // best-effort: 발행 실패해도 표시하지 않고 중단 → grace 지난 뒤 릴레이가 재시도/격리한다.
                log.warn("즉시 발행 실패 (id={}, orderId={}): {} — 릴레이 백스톱이 처리", event.getId(), orderId, e.getMessage());
                return;
            }
        }
    }
}
