package com.loopers.infrastructure.like;

/**
 * catalog-events 토픽으로 나가는 메시지 봉투. commerce-streamer의 CatalogEvent와 필드가 대응한다.
 * 이벤트 종류별로 채우는 필드가 다르다 — quantity·unitPrice는 판매(PRODUCT_SOLD), likeCount·likeDelta는
 * 좋아요 스냅샷, source는 조회(PRODUCT_VIEWED)의 유입 경로다. unitPrice는 랭킹 점수(단가×수량) 산정을 위해
 * 판매 이벤트에 싣는다.
 * <p>
 * 좋아요는 소비 측 관심사에 따라 두 표현을 함께 싣는다 — 집계(product_metrics)는 중복·유실에 강한
 * likeCount 절대값을, 랭킹(ZSET)은 누적 가산이라 likeDelta(±1)를 본다. 발행 측이 이미 아는 증감을
 * 싣지 않으면 소비 측이 이전 값을 알아내려 다른 저장소를 뒤져야 하고, 그 순간 두 소비자가 서로 묶인다.
 */
public record CatalogEventPayload(
        String eventId,
        String eventType,
        Long productId,
        Long userId,
        String occurredAt,
        Integer quantity,
        Long likeCount,
        Long likeDelta,
        Long unitPrice,
        String source
) {
    /** 수량·카운트가 없는 이벤트용 생성자. */
    public CatalogEventPayload(String eventId, String eventType, Long productId, Long userId, String occurredAt) {
        this(eventId, eventType, productId, userId, occurredAt, null, null, null, null, null);
    }

    /** 조회(PRODUCT_VIEWED)용 — 유입 경로를 싣는다. */
    public CatalogEventPayload(
            String eventId, String eventType, Long productId, Long userId, String occurredAt, String source) {
        this(eventId, eventType, productId, userId, occurredAt, null, null, null, null, source);
    }
}
