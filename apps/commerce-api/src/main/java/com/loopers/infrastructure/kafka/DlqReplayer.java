package com.loopers.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.DlqMessage;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * DLQ 재처리(재주입). {@code <topic>-dlq}에 격리된 메시지를 원본 페이로드로 복원해 원본 토픽에 다시 발행한다.
 * 어드민이 원인을 확인한 뒤 온디맨드로 트리거하며, 자동 재시도는 하지 않는다({@link com.loopers.confg.kafka.DlqPublisher} 참조).
 *
 * <p>전용 group({@value #REPLAY_GROUP})으로 읽고 커밋하므로, 다음 호출에서는 그 사이 새로 쌓인 건만 집는다(재replay 방지).
 * 재발행된 메시지가 또 실패하면 컨슈머가 다시 DLQ로 격리하므로 무한 루프는 소비 멱등으로 흡수된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqReplayer {

    private static final String REPLAY_GROUP = "dlq-replay";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final int MAX_EMPTY_POLLS = 3; // 할당 대기 + 소진 판단 여유

    private final ConsumerFactory<Object, Object> consumerFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 원본 토픽의 DLQ에 쌓인 메시지를 모두 원본 토픽으로 재발행하고, 재발행한 건수를 반환한다. */
    public int replay(String originalTopic) {
        String dlqTopic = KafkaTopics.dlq(originalTopic);
        int replayed = 0;
        try (Consumer<Object, Object> consumer = consumerFactory.createConsumer(REPLAY_GROUP, null)) {
            consumer.subscribe(List.of(dlqTopic));
            int emptyPolls = 0;
            while (emptyPolls < MAX_EMPTY_POLLS) {
                ConsumerRecords<Object, Object> records = consumer.poll(POLL_TIMEOUT);
                if (records.isEmpty()) {
                    emptyPolls++;
                    continue;
                }
                emptyPolls = 0;
                for (ConsumerRecord<Object, Object> record : records) {
                    DlqMessage message = objectMapper.readValue((byte[]) record.value(), DlqMessage.class);
                    // payload는 원본 JSON '문자열'이라 그대로 보내면 이중 인코딩된다 — 트리로 파싱해 객체로 재발행한다.
                    JsonNode payload = objectMapper.readTree(message.payload());
                    kafkaTemplate.send(message.originalTopic(), message.key(), payload);
                    replayed++;
                }
                consumer.commitSync();
            }
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "DLQ 재처리 중 오류가 발생했습니다: " + dlqTopic, e);
        }
        log.info("[DLQ-replay] {} → {} 재발행 {}건", dlqTopic, originalTopic, replayed);
        return replayed;
    }
}
