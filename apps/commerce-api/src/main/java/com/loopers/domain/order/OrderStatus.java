package com.loopers.domain.order;

public enum OrderStatus {
    /** 주문이 생성됨 — 결제 대기 상태. */
    CREATED,
    /** 결제 성공으로 확정됨. */
    PAID,
    /** 결제 실패·취소로 무효화됨 (재고·쿠폰 보상 완료). */
    CANCELED
}
