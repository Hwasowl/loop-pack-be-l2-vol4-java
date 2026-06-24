package com.loopers.infrastructure.payment;

/** PG 결제 요청/조회 응답의 data 부분. */
public record PgTransactionResponse(
    String transactionKey,
    String status,
    String reason
) {
}
