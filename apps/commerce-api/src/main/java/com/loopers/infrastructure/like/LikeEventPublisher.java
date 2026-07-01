package com.loopers.infrastructure.like;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.like.ProductLiked;
import com.loopers.domain.like.ProductUnliked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;

/**
 * 좋아요 도메인 이벤트를 커밋 확정 후 catalog-events 토픽으로 발행한다.
 * key=productId 로 같은 상품 이벤트의 파티션 순서를 보장한다.
 * (원본 product_like가 있어 유실돼도 재집계로 복구 가능하므로 Outbox 없이 직접 발행한다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventPublisher {

    private static final String TYPE_LIKED = "PRODUCT_LIKED";
    private static final String TYPE_UNLIKED = "PRODUCT_UNLIKED";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ProductLiked event) {
        publish(TYPE_LIKED, event.eventId(), event.productId(), event.userId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ProductUnliked event) {
        publish(TYPE_UNLIKED, event.eventId(), event.productId(), event.userId(), event.occurredAt());
    }

    private void publish(String eventType, String eventId, Long productId, Long userId, ZonedDateTime occurredAt) {
        CatalogEventPayload payload = new CatalogEventPayload(eventId, eventType, productId, userId, occurredAt.toString());
        try {
            kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, productId.toString(), payload);
        } catch (Exception e) {
            // 좋아요 관계(product_like)는 이미 커밋됐다. 발행 실패로 API를 500 내지 않고, 집계 이벤트만 버린다.
            // 유실은 허용 — 원본이 남아 집계 드리프트만 생기고, 필요 시 재집계로 복구할 수 있다.
            log.warn("좋아요 이벤트 발행 실패 (type={}, productId={}): {}", eventType, productId, e.getMessage());
        }
    }
}
