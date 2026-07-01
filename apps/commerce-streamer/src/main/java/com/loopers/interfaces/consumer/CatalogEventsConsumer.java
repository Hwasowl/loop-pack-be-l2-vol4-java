package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsService;
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

    @KafkaListener(
            topics = KafkaTopics.CATALOG_EVENTS,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            CatalogEvent event = parse(record);
            if (event == null) {
                continue;
            }
            productMetricsService.applyLike(event.eventId(), event.eventType(), event.productId());
        }
        acknowledgment.acknowledge();
    }

    private CatalogEvent parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), CatalogEvent.class);
        } catch (Exception e) {
            log.error("catalog-events 역직렬화 실패 (offset={}): {}", record.offset(), e.getMessage());
            return null;
        }
    }
}
