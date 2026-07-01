package com.loopers.domain.order;

/**
 * order-events 토픽으로 오가는 결제 확정 이벤트 봉투. eventType으로 완료/실패를 구분한다.
 * 소비 핸들러는 orderId만 필요하므로(주문 status로 멱등) 최소 필드만 담는다.
 */
public record OrderEventMessage(String eventType, Long orderId) {
}
