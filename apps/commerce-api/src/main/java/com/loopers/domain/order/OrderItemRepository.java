package com.loopers.domain.order;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository {
    List<OrderItem> saveAll(Collection<OrderItem> items);
    List<OrderItem> findAllByOrderId(Long orderId);
}
