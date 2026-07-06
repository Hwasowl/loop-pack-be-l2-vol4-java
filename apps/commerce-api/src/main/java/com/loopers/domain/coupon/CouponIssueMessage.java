package com.loopers.domain.coupon;

/**
 * coupon-issue-requests 토픽 메시지. key=couponTemplateId 로 템플릿별 파티션 순서(선착순)를 보장한다.
 * requestId는 유저가 결과를 조회할 번호표이자 재전달 멱등 키다.
 */
public record CouponIssueMessage(String requestId, Long userId, Long couponTemplateId) {
}
