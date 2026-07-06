package com.loopers.domain.order;

/**
 * order-events 봉투의 이벤트 종류. Producer/Consumer가 공유하는 타입 계약이다.
 * Kafka 직렬화는 enum 이름(PAYMENT_COMPLETED 등)을 그대로 쓰므로 와이어 포맷이 기존 문자열과 호환된다.
 */
public enum OrderEventType {
    PAYMENT_COMPLETED,
    PAYMENT_FAILED
}
