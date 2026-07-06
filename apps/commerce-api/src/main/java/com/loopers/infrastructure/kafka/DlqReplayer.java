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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
    private static final long SEND_TIMEOUT_SECONDS = 5;

    /** 재처리를 허용하는 원본 토픽 화이트리스트 — 임의 토픽으로의 재발행을 막는다. */
    private static final Set<String> REPLAYABLE_TOPICS = Set.of(
        KafkaTopics.CATALOG_EVENTS,
        KafkaTopics.ORDER_EVENTS,
        KafkaTopics.COUPON_ISSUE_REQUESTS,
        KafkaTopics.USER_ACTIONS
    );

    // 같은 토픽 재처리를 직렬화한다 — 같은 group을 공유한 동시 호출이 리밸런싱으로 0건 종료되는 것을 막는다.
    private final ConcurrentHashMap<String, ReentrantLock> topicLocks = new ConcurrentHashMap<>();

    private final ConsumerFactory<Object, Object> consumerFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 원본 토픽의 DLQ에 쌓인 메시지를 모두 원본 토픽으로 재발행하고, 재발행한 건수를 반환한다. */
    public int replay(String originalTopic) {
        if (!REPLAYABLE_TOPICS.contains(originalTopic)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재처리할 수 없는 토픽입니다: " + originalTopic);
        }
        String dlqTopic = KafkaTopics.dlq(originalTopic);
        ReentrantLock lock = topicLocks.computeIfAbsent(originalTopic, k -> new ReentrantLock());
        lock.lock();
        try {
            return drainAndResend(dlqTopic);
        } finally {
            lock.unlock();
        }
    }

    private int drainAndResend(String dlqTopic) {
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
                // 배치의 모든 발행이 성공한 뒤에만 오프셋을 커밋한다 — 하나라도 실패하면 커밋을 건너뛰어
                // 다음 호출에서 재시도되게 하고, 성공분만 카운트한다(커밋 전 유실 방지).
                List<CompletableFuture<?>> futures = new ArrayList<>();
                for (ConsumerRecord<Object, Object> record : records) {
                    DlqMessage message = objectMapper.readValue((byte[]) record.value(), DlqMessage.class);
                    // payload는 원본 JSON '문자열'이라 그대로 보내면 이중 인코딩된다 — 트리로 파싱해 객체로 재발행한다.
                    JsonNode payload = objectMapper.readTree(message.payload());
                    futures.add(kafkaTemplate.send(message.originalTopic(), message.key(), payload));
                }
                for (CompletableFuture<?> future : futures) {
                    future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                replayed += futures.size();
                consumer.commitSync();
            }
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "DLQ 재처리 중 오류가 발생했습니다: " + dlqTopic, e);
        }
        log.info("[DLQ-replay] {} 재발행 {}건", dlqTopic, replayed);
        return replayed;
    }
}
