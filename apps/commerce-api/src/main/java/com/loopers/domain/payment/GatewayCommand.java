package com.loopers.domain.payment;

/** PG 결제 접수 요청에 필요한 입력. cardNo는 PG 전달 용도로만 들고 저장하지 않는다. */
public record GatewayCommand(Long orderId, Long userId, CardType cardType, String cardNo, long amount) {
}
