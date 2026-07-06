package com.loopers.domain.like;

import java.time.ZonedDateTime;
import java.util.UUID;

/** 좋아요 취소라는 사실. 좋아요 관계가 실제로 삭제됐을 때만 발행된다. */
public record ProductUnliked(String eventId, Long productId, Long userId, ZonedDateTime occurredAt) {

    public static ProductUnliked of(Long productId, Long userId) {
        return new ProductUnliked(UUID.randomUUID().toString(), productId, userId, ZonedDateTime.now());
    }
}
