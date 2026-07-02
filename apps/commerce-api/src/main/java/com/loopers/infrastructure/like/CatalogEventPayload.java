package com.loopers.infrastructure.like;

/**
 * catalog-events 토픽으로 나가는 메시지 봉투. commerce-streamer의 CatalogEvent와 필드가 대응한다.
 * 이벤트 종류별로 채우는 필드가 다르다 — quantity는 판매(PRODUCT_SOLD), likeCount는 좋아요 스냅샷,
 * 조회(PRODUCT_VIEWED)는 둘 다 null이다.
 */
public record CatalogEventPayload(
        String eventId,
        String eventType,
        Long productId,
        Long userId,
        String occurredAt,
        Integer quantity,
        Long likeCount
) {
    /** 수량·카운트가 없는 이벤트(조회)용 생성자. */
    public CatalogEventPayload(String eventId, String eventType, Long productId, Long userId, String occurredAt) {
        this(eventId, eventType, productId, userId, occurredAt, null, null);
    }
}
