package com.loopers.domain.payment;

/** 결제 실패 확정 도메인 이벤트. 주문 도메인이 구독해 보상(재고·쿠폰 복원) 후 CANCELED로 반영한다. */
public record PaymentFailed(Long orderId, String reason) {
}
