package com.loopers.domain.like;

import java.time.ZonedDateTime;
import java.util.UUID;

/** 좋아요 등록이라는 사실. 좋아요 관계가 실제로 새로 생겼을 때만 발행된다. */
public record ProductLiked(String eventId, Long productId, Long userId, ZonedDateTime occurredAt) {

    public static ProductLiked of(Long productId, Long userId) {
        return new ProductLiked(UUID.randomUUID().toString(), productId, userId, ZonedDateTime.now());
    }
}
