package com.loopers.domain.product;

import java.time.ZonedDateTime;
import java.util.UUID;

/** 상품 상세 조회라는 사실. 상세 조회 요청마다 발행된다(캐시 적중 여부와 무관). */
public record ProductViewed(String eventId, Long productId, ZonedDateTime occurredAt) {

    public static ProductViewed of(Long productId) {
        return new ProductViewed(UUID.randomUUID().toString(), productId, ZonedDateTime.now());
    }
}
