package com.loopers.confg.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 처리에 실패한 메시지를 원본 토픽의 dead-letter 토픽(&lt;topic&gt;-dlq)으로 재발행한다.
 * 컨슈머가 실패를 잡아 여기로 넘기면, 해당 파티션은 막히지 않고 다음 메시지를 계속 처리한다.
 * DLQ는 자동 재시도하지 않는다 — lag을 모니터링하다 쌓이면 사람이 원인을 보고 재발행을 결정한다(멘토 조언).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public void publish(String originalTopic, ConsumerRecord<?, ?> record, Exception cause) {
        String key = record.key() == null ? null : record.key().toString();
        publish(originalTopic, key, stringify(record.value()), cause);
    }

    /** 컨슈머 레코드가 아닌 원본(예: outbox 행)을 격리할 때 쓰는 오버로드. */
    public void publish(String originalTopic, String key, String payload, Exception cause) {
        String dlqTopic = KafkaTopics.dlq(originalTopic);
        DlqMessage message = new DlqMessage(originalTopic, key, payload, cause.getMessage());
        try {
            kafkaTemplate.send(dlqTopic, key, message).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.warn("[DLQ] {} → {} (key={}): {}", originalTopic, dlqTopic, key, cause.getMessage());
                } else {
                    log.error("[DLQ] 비동기 발행 실패 topic={} key={}: {}", dlqTopic, key, ex.getMessage());
                }
            });
        } catch (Exception e) {
            // DLQ 발행마저 실패하면 원본 메시지는 로그로만 남긴다(무한 재시도로 파티션을 막지 않는다).
            log.error("[DLQ] 발행 실패 topic={} key={}: {} / 원본 길이={}", dlqTopic, key, e.getMessage(), payload == null ? 0 : payload.length());
        }
    }

    /**
     * DLQ 적재의 성공 여부를 확인해 반환한다(브로커 ack까지 대기). 릴레이가 outbox를 FAILED로 종결하기 전에
     * "정말 DLQ에 담겼는지"를 확인하는 용도 — 브로커 다운으로 DLQ 발행도 실패하면 false를 돌려
     * 호출자가 outbox를 FAILED로 굳히지 않고 PENDING으로 두어 재시도하게 한다.
     */
    public boolean publishSync(String originalTopic, String key, String payload, Exception cause) {
        String dlqTopic = KafkaTopics.dlq(originalTopic);
        DlqMessage message = new DlqMessage(originalTopic, key, payload, cause.getMessage());
        try {
            kafkaTemplate.send(dlqTopic, key, message).get(5, TimeUnit.SECONDS);
            log.warn("[DLQ] {} → {} (key={}): {}", originalTopic, dlqTopic, key, cause.getMessage());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[DLQ] 격리 중단 topic={} key={}", dlqTopic, key);
            return false;
        } catch (Exception e) {
            log.error("[DLQ] 격리 실패 — outbox FAILED 종결 보류 topic={} key={}: {}", dlqTopic, key, e.getMessage());
            return false;
        }
    }

    private String stringify(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
