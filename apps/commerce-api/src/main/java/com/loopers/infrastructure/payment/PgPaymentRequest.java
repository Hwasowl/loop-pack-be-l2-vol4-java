package com.loopers.infrastructure.payment;

/** PG(pg-simulator) 결제 요청 본문. orderId는 6자 이상 문자열(zero-pad), amount는 정수. */
public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    long amount,
    String callbackUrl
) {
}
