package com.loopers.domain.useraction;

import java.time.ZonedDateTime;

/**
 * 유저 행동 로그 이벤트(조회/좋아요/주문 등). 부가(비핵심) 관심사라 @Async로 절연 발행한다.
 * 유실 허용 — 원본 상태(주문/좋아요 등)는 별도로 남으므로 로그는 근사로 충분하다.
 */
public record UserActionEvent(Long userId, String action, Long targetId, String occurredAt) {

    public static UserActionEvent of(Long userId, String action, Long targetId) {
        return new UserActionEvent(userId, action, targetId, ZonedDateTime.now().toString());
    }
}
