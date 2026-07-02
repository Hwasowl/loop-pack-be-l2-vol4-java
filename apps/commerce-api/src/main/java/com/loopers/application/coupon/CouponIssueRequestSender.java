package com.loopers.application.coupon;

/**
 * 선착순 발급 요청을 비동기 처리 파이프(Kafka)로 넘기는 포트.
 * 구현은 infrastructure에 두어 application이 메시징 기술에 의존하지 않게 한다(DIP).
 */
public interface CouponIssueRequestSender {
    void send(String requestId, Long userId, Long couponTemplateId);
}
