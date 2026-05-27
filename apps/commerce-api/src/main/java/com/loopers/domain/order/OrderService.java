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

    /**
     * 주문 생성 — Order는 애그리거트 루트이고 OrderItem은 같은 애그리거트라
     * {@code OrderModel.items}에 @OneToMany cascade=ALL로 매핑돼 있어 한 번의 save로 함께 영속된다.
     */
    @Transactional
    public OrderModel place(Long userId, List<OrderItem> items) {
        return orderRepository.save(new OrderModel(userId, items));
    }

    @Transactional(readOnly = true)
    public OrderModel getById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }
}
