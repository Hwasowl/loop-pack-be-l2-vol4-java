package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsService;
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
 * catalog-events 소비 → product_metrics 집계.
 * 배치 리스너로 받아 개별 이벤트를 순차 처리하고, 배치 전체 처리 후 수동 커밋한다.
 * (파티션 key=productId 라 같은 상품 이벤트는 순서 보장된다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventsConsumer {

    private final ProductMetricsService productMetricsService;
    private final ObjectMapper objectMapper;
    private final DlqPublisher dlqPublisher;

    @KafkaListener(
            topics = KafkaTopics.CATALOG_EVENTS,
            groupId = "product-metrics-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                CatalogEvent event = objectMapper.readValue((byte[]) record.value(), CatalogEvent.class);
                switch (event.eventType()) {
                    case "PRODUCT_LIKE_COUNT_CHANGED" ->
                            productMetricsService.applyLikeSnapshot(
                                    event.productId(), event.likeCount(), ZonedDateTime.parse(event.occurredAt()));
                    case "PRODUCT_VIEWED" ->
                            productMetricsService.applyView(event.productId());
                    case "PRODUCT_SOLD" ->
                            productMetricsService.applySold(event.eventId(), event.productId(), event.quantity());
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
