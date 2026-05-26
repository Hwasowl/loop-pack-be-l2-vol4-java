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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderService orderService;

    @DisplayName("placeInitial 시")
    @Nested
    class PlaceInitial {

        @DisplayName("Order 저장 후 items에 orderId를 주입하고 일괄 저장한다 (CREATED 반환)")
        @Test
        void savesOrderAndAssignsOrderIdToItems() {
            OrderItem item1 = new OrderItem(1L, "후드", 10_000L, 2);
            OrderItem item2 = new OrderItem(2L, "맨투맨", 15_000L, 1);
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> {
                OrderModel arg = inv.getArgument(0);
                // 영속화 후 ID가 부여된 것처럼 — Mock으로는 동일 인스턴스 반환만 가능
                return arg;
            });

            OrderModel result = orderService.placeInitial(1L, 35_000L, List.of(item1, item2));

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(result.getTotalAmount()).isEqualTo(35_000L)
            );
            verify(orderItemRepository, times(1)).saveAll(anyCollection());
        }
    }

    @DisplayName("markSucceeded 시")
    @Nested
    class MarkSucceeded {

        @DisplayName("존재하는 CREATED 주문이면 status를 SUCCEEDED로 전이하고 OrderModel을 반환한다")
        @Test
        void transitionsToSucceeded() {
            OrderModel order = new OrderModel(1L, 10_000L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            OrderModel result = orderService.markSucceeded(1L);

            assertAll(
                () -> assertThat(result).isSameAs(order),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.SUCCEEDED)
            );
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenMissing() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> orderService.markSucceeded(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("markFailed 시")
    @Nested
    class MarkFailed {

        @DisplayName("존재하는 CREATED 주문이면 status를 FAILED로 전이하고 사유를 기록한다")
        @Test
        void transitionsToFailedWithReason() {
            OrderModel order = new OrderModel(1L, 10_000L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            orderService.markFailed(1L, "재고가 부족합니다.");

            assertAll(
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED),
                () -> assertThat(order.getFailureReason()).isEqualTo("재고가 부족합니다.")
            );
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenMissing() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> orderService.markFailed(999L, "x"));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getById 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면 그대로 반환한다")
        @Test
        void returnsOrder_whenIdExists() {
            OrderModel order = new OrderModel(1L, 10_000L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThat(orderService.getById(1L)).isSameAs(order);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class, () -> orderService.getById(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
