package com.loopers.domain.coupon;

/** coupon-issue-requests 토픽 메시지. key=couponTemplateId 로 템플릿별 파티션 순서(선착순)를 보장한다. */
public record CouponIssueMessage(Long userId, Long couponTemplateId) {
}
