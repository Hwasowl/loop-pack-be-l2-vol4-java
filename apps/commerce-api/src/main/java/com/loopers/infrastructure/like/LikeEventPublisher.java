package com.loopers.infrastructure.like;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.ProductLiked;
import com.loopers.domain.like.ProductUnliked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;

/**
 * 좋아요 등록/취소가 커밋된 뒤, 그 상품의 <b>현재 총 좋아요 수 스냅샷</b>을 catalog-events로 발행한다.
 * 증감분(±1)이 아니라 절대값을 보내므로 소비 측은 최신-우선(occurredAt 비교)으로 덮어쓰기만 하면 된다.
 * 이 방식은 중복·유실에 강하다(다음 스냅샷이 자동 교정). 대신 순서 역전 위험은 occurredAt 토큰으로 막는다.
 * key=productId 로 같은 상품 이벤트의 파티션 순서를 보장한다.
 * (원본 product_like가 있어 유실돼도 재집계로 복구 가능하므로 Outbox 없이 직접 발행한다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventPublisher {

    private static final String TYPE_LIKE_COUNT_CHANGED = "PRODUCT_LIKE_COUNT_CHANGED";

    private final LikeRepository likeRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void on(ProductLiked event) {
        publish(event.eventId(), event.productId(), event.userId(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void on(ProductUnliked event) {
        publish(event.eventId(), event.productId(), event.userId(), event.occurredAt());
    }

    private void publish(String eventId, Long productId, Long userId, ZonedDateTime occurredAt) {
        long likeCount = likeRepository.countByProductId(productId);
        CatalogEventPayload payload = new CatalogEventPayload(
                eventId, TYPE_LIKE_COUNT_CHANGED, productId, userId, occurredAt.toString(), null, likeCount);
        try {
            kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, productId.toString(), payload);
        } catch (Exception e) {
            // 좋아요 관계(product_like)는 이미 커밋됐다. 발행 실패로 API를 500 내지 않고, 집계 이벤트만 버린다.
            // 유실은 허용 — 원본이 남아 다음 스냅샷/재집계로 복구할 수 있다.
            log.warn("좋아요 스냅샷 발행 실패 (productId={}): {}", productId, e.getMessage());
        }
    }
}
