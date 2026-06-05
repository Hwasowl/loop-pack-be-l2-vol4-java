package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

public record OrderItemInfo(
    Long productId,
    String productName,
    Long unitPrice,
    Integer quantity,
    Long subtotal,
    Long issuedCouponId,
    Long discountAmount,
    Long payable
) {
    public static OrderItemInfo from(OrderItem item) {
        return new OrderItemInfo(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice().value(),
            item.getQuantity(),
            item.subtotal().value(),
            item.getIssuedCouponId(),
            item.getDiscountAmount().value(),
            item.payable().value()
        );
    }
}
