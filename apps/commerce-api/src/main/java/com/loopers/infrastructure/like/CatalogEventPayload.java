package com.loopers.infrastructure.like;

/**
 * catalog-events 토픽으로 나가는 메시지 봉투. commerce-streamer의 CatalogEvent와 필드가 대응한다.
 * quantity는 판매(PRODUCT_SOLD)에서만 채우고 좋아요/조회는 null이다.
 */
public record CatalogEventPayload(
        String eventId,
        String eventType,
        Long productId,
        Long userId,
        String occurredAt,
        Integer quantity
) {
    /** 수량이 없는 이벤트(좋아요/조회)용 생성자. */
    public CatalogEventPayload(String eventId, String eventType, Long productId, Long userId, String occurredAt) {
        this(eventId, eventType, productId, userId, occurredAt, null);
    }
}
