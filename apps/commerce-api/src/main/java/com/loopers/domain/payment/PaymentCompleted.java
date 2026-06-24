package com.loopers.domain.payment;

/** 결제 성공 확정 도메인 이벤트. 주문 도메인이 구독해 PAID로 반영한다. */
public record PaymentCompleted(Long orderId) {
}
