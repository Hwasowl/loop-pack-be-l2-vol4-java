package com.loopers.infrastructure.product;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.product.ProductViewed;
import com.loopers.infrastructure.like.CatalogEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 상품 조회 이벤트를 catalog-events 토픽으로 발행한다.
 * 조회는 쓰기(커밋)가 없는 흐름이라 AFTER_COMMIT이 아닌 일반 이벤트로 즉시 발행한다.
 * key=productId 로 좋아요 이벤트와 같은 파티션에 실려 상품별 순서가 함께 유지된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewEventPublisher {

    private static final String TYPE_VIEWED = "PRODUCT_VIEWED";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Async
    @EventListener
    public void on(ProductViewed event) {
        CatalogEventPayload payload = new CatalogEventPayload(
                event.eventId(), TYPE_VIEWED, event.productId(), null, event.occurredAt().toString());
        try {
            kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, event.productId().toString(), payload);
        } catch (Exception e) {
            // 조회 집계는 부가 기능이라 발행 실패가 상세 조회(본 기능)를 깨면 안 된다. 유실은 근사 지표라 감수한다.
            log.warn("조회 이벤트 발행 실패 (productId={}): {}", event.productId(), e.getMessage());
        }
    }
}
