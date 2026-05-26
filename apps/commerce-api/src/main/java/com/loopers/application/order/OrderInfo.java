package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    Long totalAmount,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {
    public static OrderInfo from(OrderModel order, List<OrderItem> items) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            items.stream().map(OrderItemInfo::from).toList(),
            order.getCreatedAt()
        );
    }
}
