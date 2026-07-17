package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingService;
import com.loopers.confg.kafka.DlqPublisher;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * catalog-events 소비 → 랭킹 ZSET 갱신.
 * <p>
 * CatalogEventsConsumer(product_metrics 집계)와 <b>같은 토픽을 다른 group.id로</b> 읽는다. 둘은 같은 이벤트의
 * 서로 다른 표현이지 한 흐름이 아니다 — 한쪽은 영속 집계(RDB), 한쪽은 조회용 리드 모델(Redis)이다.
 * 그룹을 나눠야 각자의 오프셋·재시도·장애가 독립한다. 한 리스너에서 둘 다 처리하면 RDB 커밋 후 Redis 반영 전
 * 죽었을 때, 재소비해도 집계 쪽이 "이미 반영됨"으로 판단해 랭킹 델타가 영구 유실된다.
 * <p>
 * 멱등을 두지 않는다. 랭킹은 근사가 허용되는 데이터라 중복 소비로 점수가 조금 밀리는 것보다,
 * 조회량만큼 멱등 장부를 쌓는 비용이 크다. 정확도가 필요해지면 SOT(시간 버킷 집계)로 덮어쓰는 쪽이 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingEventsConsumer {

    private final RankingService rankingService;
    private final ObjectMapper objectMapper;
    private final DlqPublisher dlqPublisher;

    @KafkaListener(
            topics = KafkaTopics.CATALOG_EVENTS,
            groupId = "ranking-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                CatalogEvent event = objectMapper.readValue((byte[]) record.value(), CatalogEvent.class);
                ZonedDateTime occurredAt = ZonedDateTime.parse(event.occurredAt());
                switch (event.eventType()) {
                    case "PRODUCT_LIKE_COUNT_CHANGED" -> {
                        if (event.likeDelta() == null) {
                            throw new IllegalArgumentException("likeDelta 없는 좋아요 이벤트: eventId=" + event.eventId());
                        }
                        rankingService.applyLikeDelta(occurredAt, event.productId(), event.likeDelta());
                    }
                    case "PRODUCT_VIEWED" -> rankingService.applyView(occurredAt, event.productId());
                    case "PRODUCT_SOLD" -> {
                        long amount = (long) event.quantity() * (event.unitPrice() == null ? 0L : event.unitPrice());
                        rankingService.applyOrder(occurredAt, event.productId(), amount);
                    }
                    default -> log.warn("알 수 없는 catalog 이벤트 타입 (offset={}): {}", record.offset(), event.eventType());
                }
            } catch (Exception e) {
                // 역직렬화·처리 실패 메시지는 DLQ로 격리한다 — 파티션을 막지 않고 다음 메시지를 계속 처리한다.
                dlqPublisher.publish(KafkaTopics.CATALOG_EVENTS, record, e);
            }
        }
        acknowledgment.acknowledge();
    }
}
