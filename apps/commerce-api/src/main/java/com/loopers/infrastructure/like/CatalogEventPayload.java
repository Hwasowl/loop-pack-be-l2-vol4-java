package com.loopers.infrastructure.like;

/** catalog-events 토픽으로 나가는 메시지 봉투. commerce-streamer의 CatalogEvent와 필드가 대응한다. */
public record CatalogEventPayload(
        String eventId,
        String eventType,
        Long productId,
        Long userId,
        String occurredAt
) {
}
