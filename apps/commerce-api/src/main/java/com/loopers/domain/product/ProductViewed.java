package com.loopers.domain.product;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 상품 상세 조회라는 사실. 상세 조회 요청마다 발행된다(캐시 적중 여부와 무관).
 * 어디를 거쳐 들어왔는지(source)를 함께 싣는다 — 조회 1건의 값어치가 경로마다 다르기 때문이다.
 */
public record ProductViewed(String eventId, Long productId, ViewSource source, ZonedDateTime occurredAt) {

    public static ProductViewed of(Long productId, ViewSource source) {
        return new ProductViewed(UUID.randomUUID().toString(), productId, source, ZonedDateTime.now());
    }
}
