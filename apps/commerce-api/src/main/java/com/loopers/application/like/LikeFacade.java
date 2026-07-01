package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.ProductLiked;
import com.loopers.domain.like.ProductUnliked;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 좋아요 등록. 관계(product_like) 저장이 실제로 일어났을 때만 이벤트를 발행한다.
     * Facade가 트랜잭션 경계를 쥐어, 커밋이 확정된 뒤(AFTER_COMMIT)에만 이벤트가 Kafka로 나가게 한다.
     * 좋아요 수 집계는 commerce-streamer가 이 이벤트를 소비해 product_metrics에 반영한다.
     */
    @Transactional
    public void like(Long userId, Long productId) {
        productService.requireExists(productId);
        if (likeService.like(userId, productId)) {
            eventPublisher.publishEvent(ProductLiked.of(productId, userId));
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        if (likeService.unlike(userId, productId)) {
            eventPublisher.publishEvent(ProductUnliked.of(productId, userId));
        } else {
            productService.requireExists(productId);
        }
    }
}
