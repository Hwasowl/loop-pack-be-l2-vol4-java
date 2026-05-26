package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderModel placeInitial(Long userId, Long totalAmount, List<OrderItem> items) {
        OrderModel order = orderRepository.save(new OrderModel(userId, totalAmount));
        items.forEach(item -> item.assignOrderId(order.getId()));
        orderItemRepository.saveAll(items);
        return order;
    }

    @Transactional
    public OrderModel markSucceeded(Long orderId) {
        OrderModel order = loadOrThrow(orderId);
        order.markSucceeded();
        return order;
    }

    @Transactional
    public void markFailed(Long orderId, String reason) {
        loadOrThrow(orderId).markFailed(reason);
    }

    @Transactional(readOnly = true)
    public OrderModel getById(Long id) {
        return loadOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getItemsByOrderId(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    private OrderModel loadOrThrow(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }
}
