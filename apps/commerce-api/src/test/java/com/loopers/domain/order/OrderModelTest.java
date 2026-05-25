package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    private static final Long USER_ID = 1L;

    @DisplayName("주문 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 유저와 항목으로 생성하면 status=CREATED, totalAmount는 항목 소계의 합이다")
        @Test
        void createsOrder_whenValid() {
            // given
            OrderItem item1 = new OrderItem(1L, "후드", 10_000L, 2);
            OrderItem item2 = new OrderItem(2L, "맨투맨", 15_000L, 1);

            // when
            OrderModel order = new OrderModel(USER_ID, List.of(item1, item2));

            // then
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(order.getTotalAmount()).isEqualTo(10_000L * 2 + 15_000L),
                () -> assertThat(order.getItems()).hasSize(2),
                () -> assertThat(order.getItems().get(0).getOrder()).isSameAs(order),
                () -> assertThat(order.getItems().get(1).getOrder()).isSameAs(order)
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // given
            List<OrderItem> items = List.of(new OrderItem(1L, "후드", 10_000L, 1));

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new OrderModel(null, items));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("항목 목록이 null이거나 비어있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, null)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, List.of())).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }
}
