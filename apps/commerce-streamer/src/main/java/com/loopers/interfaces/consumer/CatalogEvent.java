package com.loopers.interfaces.consumer;

/**
 * catalog-events 토픽 메시지 봉투. 한 토픽에 좋아요/조회 등 여러 이벤트가 섞이므로
 * eventType으로 종류를 구분한다. (타입 헤더를 끈 상태라 payload에 종류를 담는다)
 * <p>
 * 좋아요는 소비자마다 보는 필드가 다르다 — 집계는 likeCount(절대값), 랭킹은 likeDelta(±1).
 */
public record CatalogEvent(
        String eventId,
        String eventType,
        Long productId,
        Long userId,
        String occurredAt,
        Integer quantity,
        Long likeCount,
        Long likeDelta,
        Long unitPrice
) {
}
