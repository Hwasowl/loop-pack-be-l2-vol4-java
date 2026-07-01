package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 결제 확정과 같은 트랜잭션에서 outbox 행이 원자적으로 적재되는지 검증한다(dual-write 갭 봉합).
 * 실제 Kafka 발행(릴레이)은 @Profile(!test)라 여기선 돌지 않으므로 published_at은 null이어야 한다.
 */
@SpringBootTest
class PaymentOutboxIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OutboxJpaRepository outboxJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제 성공을 확정하면 PAYMENT_COMPLETED 아웃박스 행이 미발행 상태로 적재된다")
    @Test
    void appendsCompletedOutbox_onConfirmSuccess() {
        // given
        OrderModel order = orderRepository.save(
            new OrderModel(USER_ID, List.of(new OrderItem(100L, "상품", 1_000L, 1)), null, Money.ZERO));
        PaymentModel payment = new PaymentModel(order.getId(), USER_ID, CardType.SAMSUNG, order.getFinalAmount());
        payment.assignTransactionKey("tx-outbox");
        paymentRepository.save(payment);

        // when
        paymentService.confirm("tx-outbox", true, null);

        // then
        List<OutboxEvent> outbox = outboxJpaRepository.findAll();
        assertThat(outbox).hasSize(1);
        OutboxEvent row = outbox.get(0);
        assertAll(
            () -> assertThat(row.getEventType()).isEqualTo("PAYMENT_COMPLETED"),
            () -> assertThat(row.getAggregateId()).isEqualTo(order.getId()),
            () -> assertThat(row.getPublishedAt()).isNull()
        );
    }
}
