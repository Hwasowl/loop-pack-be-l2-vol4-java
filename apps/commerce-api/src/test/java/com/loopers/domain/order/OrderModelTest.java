package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    private static final Long USER_ID = 1L;
    private static final Long TOTAL_AMOUNT = 35_000L;

    private OrderModel newOrder() {
        return new OrderModel(USER_ID, TOTAL_AMOUNT);
    }

    @DisplayName("주문 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 유저와 총액으로 생성하면 status=CREATED, failureReason=null로 초기화된다")
        @Test
        void createsOrder_whenValid() {
            OrderModel order = new OrderModel(USER_ID, TOTAL_AMOUNT);

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(order.getTotalAmount()).isEqualTo(TOTAL_AMOUNT),
                () -> assertThat(order.getFailureReason()).isNull()
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException ex = assertThrows(CoreException.class, () -> new OrderModel(null, TOTAL_AMOUNT));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalAmount가 null이거나 음수이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenTotalAmountIsInvalid() {
            assertAll(
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, null)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(assertThrows(CoreException.class, () -> new OrderModel(USER_ID, -1L)).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
            );
        }
    }

    @DisplayName("상태 전이 시")
    @Nested
    class StateTransition {

        @DisplayName("CREATED 상태에서 markSucceeded를 호출하면 SUCCEEDED로 전이한다")
        @Test
        void marksSucceeded_whenCreated() {
            OrderModel order = newOrder();

            order.markSucceeded();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.SUCCEEDED);
        }

        @DisplayName("CREATED 상태에서 markFailed를 호출하면 FAILED로 전이하고 failureReason이 기록된다")
        @Test
        void marksFailed_whenCreated() {
            OrderModel order = newOrder();

            order.markFailed("재고가 부족합니다.");

            assertAll(
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED),
                () -> assertThat(order.getFailureReason()).isEqualTo("재고가 부족합니다.")
            );
        }

        @DisplayName("이미 SUCCEEDED인 주문은 다시 전이할 수 없다 (CONFLICT)")
        @Test
        void throwsConflict_whenAlreadySucceeded() {
            OrderModel order = newOrder();
            order.markSucceeded();

            assertAll(
                () -> assertThat(assertThrows(CoreException.class, order::markSucceeded).getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(assertThrows(CoreException.class, () -> order.markFailed("x")).getErrorType()).isEqualTo(ErrorType.CONFLICT)
            );
        }

        @DisplayName("이미 FAILED인 주문은 다시 전이할 수 없다 (CONFLICT)")
        @Test
        void throwsConflict_whenAlreadyFailed() {
            OrderModel order = newOrder();
            order.markFailed("x");

            assertAll(
                () -> assertThat(assertThrows(CoreException.class, order::markSucceeded).getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(assertThrows(CoreException.class, () -> order.markFailed("y")).getErrorType()).isEqualTo(ErrorType.CONFLICT)
            );
        }
    }
}
