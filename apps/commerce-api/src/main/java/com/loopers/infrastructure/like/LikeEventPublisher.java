package com.loopers.infrastructure.like;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.like.ProductLiked;
import com.loopers.domain.like.ProductUnliked;
import lombok.RequiredArgsConstructor;
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
        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, productId.toString(), payload);
    }
}
