package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * user-actions 수집기. 유저 행동 로그를 별도 consumer group으로 소비한다(관심사별 분리).
 * 로그성 데이터라 RDB에 적재하지 않고 구조적 로그로 남긴다 — 운영에선 수집기가 ELK/S3로 포워드한다.
 * 토픽 자체가 append-only 로그이므로 유실/중복에 관대하다(멱등 테이블 불필요).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.USER_ACTIONS,
            groupId = "user-action-collector",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            UserActionMessage action = parse(record);
            if (action == null) {
                continue;
            }
            log.info("[user-action] userId={} action={} targetId={} at={}",
                    action.userId(), action.action(), action.targetId(), action.occurredAt());
        }
        acknowledgment.acknowledge();
    }

    private UserActionMessage parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), UserActionMessage.class);
        } catch (Exception e) {
            log.error("user-actions 역직렬화 실패 (offset={}): {}", record.offset(), e.getMessage());
            return null;
        }
    }
}
