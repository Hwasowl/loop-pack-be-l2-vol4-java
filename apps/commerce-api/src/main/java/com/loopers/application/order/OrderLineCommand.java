package com.loopers.application.order;

/** couponId는 해당 항목에 적용할 발급 쿠폰(IssuedCoupon) id. 미적용 시 null. */
public record OrderLineCommand(Long productId, Integer quantity, Long couponId) {
}
