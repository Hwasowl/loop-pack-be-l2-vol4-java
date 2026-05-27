package com.loopers.domain.order;

public enum OrderFailureReason {
    /** 재고 부족 — decreaseAll 도중 CONFLICT */
    STOCK_SHORTAGE,
    /** 재고 행 없음 — decreaseAll 도중 NOT_FOUND */
    STOCK_NOT_FOUND,
    /** 주문 상태 확정 단계 실패 — markSucceeded 등 */
    ORDER_FINALIZE_FAILED,
    /** 위 어느 분류에도 해당하지 않는 예외 (의도치 않은 RuntimeException 포함) */
    UNKNOWN
}
