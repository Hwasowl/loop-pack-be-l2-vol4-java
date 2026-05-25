package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @DisplayName("주문 생성 시 Repository가 저장한 주문을 그대로 반환한다")
    @Test
    void place_returnsSavedOrder() {
        // given
        List<OrderItem> items = List.of(new OrderItem(1L, "후드", 10_000L, 2));
        when(orderRepository.save(any(OrderModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        OrderModel result = orderService.place(1L, items);

        // then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.getTotalAmount()).isEqualTo(20_000L);
    }

    @DisplayName("ID로 조회 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면 그대로 반환한다")
        @Test
        void returnsOrder_whenIdExists() {
            // given
            OrderModel order = new OrderModel(1L, List.of(new OrderItem(1L, "후드", 10_000L, 1)));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // when
            OrderModel result = orderService.getById(1L);

            // then
            assertThat(result).isSameAs(order);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // given
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> orderService.getById(999L));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
