package com.loopers.domain.payment;

public enum PaymentStatus {
    /** 결제 접수됨 — PG 처리 대기/진행 중. */
    PENDING,
    /** 결제 성공으로 확정됨. */
    SUCCESS,
    /** 결제 실패로 확정됨 (한도초과·잘못된 카드·시스템 오류). */
    FAILED
}
