package com.loopers.infrastructure.payment;

/** orderId는 PG가 6자 이상 문자열을 요구하므로 zero-pad해 보낸다. */
public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    long amount,
    String callbackUrl
) {
}
