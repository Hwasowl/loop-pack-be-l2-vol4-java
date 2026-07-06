package com.loopers.domain.coupon;

/** 선착순 발급 처리 결과. 비동기 처리라 예외 대신 결과 값으로 표현해 컨슈머가 로깅·분기한다. */
public enum CouponIssueOutcome {
    ISSUED,
    SOLD_OUT,
    DUPLICATE,
    EXPIRED,
    NOT_FOUND
}
