package com.loopers.infrastructure.like;

/**
 * catalog-events 토픽으로 나가는 메시지 봉투. commerce-streamer의 CatalogEvent와 필드가 대응한다.
 * 이벤트 종류별로 채우는 필드가 다르다 — quantity·unitPrice는 판매(PRODUCT_SOLD), likeCount는 좋아요 스냅샷,
 * 조회(PRODUCT_VIEWED)는 모두 null이다. unitPrice는 랭킹 점수(단가×수량) 산정을 위해 판매 이벤트에 싣는다.
 */
public record CatalogEventPayload(
        String eventId,
        String eventType,
        Long productId,
        Long userId,
        String occurredAt,
        Integer quantity,
        Long likeCount,
        Long unitPrice
) {
    /** 수량·카운트가 없는 이벤트(조회)용 생성자. */
    public CatalogEventPayload(String eventId, String eventType, Long productId, Long userId, String occurredAt) {
        this(eventId, eventType, productId, userId, occurredAt, null, null, null);
    }
}
