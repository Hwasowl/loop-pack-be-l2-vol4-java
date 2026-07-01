package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.OrderEventMessage;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.infrastructure.outbox.OutboxRelay;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * order-events 배선 레벨(임베디드 Kafka) 검증 — outbox → 릴레이 → 실제 브로커 → @KafkaListener → 주문 반영.
 * 직렬화 계약(JsonSerializer↔ByteArrayDeserializer)과 리스너 배선은 Mock으로 못 잡으므로 여기서만 검증한다.
 * 비즈니스 분기/멱등은 단위(핸들러)·H2 통합에서 이미 커버 — 여기선 해피패스 2개만.
 */
@SpringBootTest(properties = {
    "payment.order-consumer=kafka",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.properties.auto.offset.reset=earliest"
})
@EmbeddedKafka(topics = "order-events", partitions = 1)
class OrderEventsKafkaFlowTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private IssuedCouponRepository issuedCouponRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void appendOutbox(String eventType, Long orderId) {
        try {
            String payload = objectMapper.writeValueAsString(new OrderEventMessage(eventType, orderId));
            outboxRepository.save(new OutboxEvent(orderId, eventType, payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 소비는 비동기라, 조건이 충족될 때까지(최대 10초) 폴링한다. */
    private void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("조건이 시간 내에 충족되지 않았습니다");
    }

    @DisplayName("PAYMENT_COMPLETED 이벤트가 브로커를 거쳐 소비되면 주문이 PAID가 된다")
    @Test
    void consumesCompleted_marksOrderPaid() {
        // given
        OrderModel order = orderRepository.save(
            new OrderModel(USER_ID, List.of(new OrderItem(100L, "상품", 1_000L, 1)), null, Money.ZERO));
        appendOutbox("PAYMENT_COMPLETED", order.getId());

        // when — 릴레이가 실제(임베디드) 브로커로 발행
        outboxRelay.relay();

        // then — 컨슈머가 비동기로 받아 처리해 주문이 PAID가 될 때까지 대기
        awaitUntil(() -> orderRepository.findById(order.getId())
            .map(o -> o.getStatus() == OrderStatus.PAID).orElse(false));
    }

    @DisplayName("PAYMENT_FAILED 이벤트가 소비되면 재고·쿠폰이 복원되고 주문이 CANCELED가 된다")
    @Test
    void consumesFailed_compensatesAndCancels() {
        // given
        stockRepository.save(new StockModel(100L, 10));
        IssuedCoupon coupon = new IssuedCoupon(USER_ID, 1L);
        coupon.use();
        issuedCouponRepository.save(coupon);
        OrderModel order = orderRepository.save(
            new OrderModel(USER_ID, List.of(new OrderItem(100L, "상품", 1_000L, 2)), coupon.getId(), Money.of(500L)));
        appendOutbox("PAYMENT_FAILED", order.getId());

        // when
        outboxRelay.relay();

        // then
        awaitUntil(() -> orderRepository.findById(order.getId())
            .map(o -> o.getStatus() == OrderStatus.CANCELED).orElse(false));
        assertAll(
            () -> assertThat(stockRepository.findByProductId(100L).orElseThrow().getQuantity()).isEqualTo(12),
            () -> assertThat(issuedCouponRepository.findById(coupon.getId()).orElseThrow().getStatus()).isEqualTo(CouponStatus.AVAILABLE)
        );
    }
}
