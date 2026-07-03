package com.loopers.infrastructure.product;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.infrastructure.like.CatalogEventPayload;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 확정(AFTER_COMMIT) → 주문 라인아이템별 PRODUCT_SOLD 발행 배선 검증.
 * 실제 브로커 대신 KafkaTemplate을 Mock으로 두고, 상품별로 한 건씩 나가는지·페이로드가 맞는지 본다.
 * (테스트에 트랜잭션을 걸지 않는다 — confirm()이 커밋돼야 AFTER_COMMIT 리스너가 발화한다)
 */
@SpringBootTest
class ProductSoldEventPublisherIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제 성공을 확정하면 주문의 상품마다 수량을 담은 PRODUCT_SOLD 이벤트가 catalog-events로 발행된다")
    @Test
    void publishesSoldPerItem_onConfirmSuccess() {
        // given
        OrderModel order = orderRepository.save(new OrderModel(
            USER_ID,
            List.of(new OrderItem(100L, "상품-100", 1_000L, 2), new OrderItem(200L, "상품-200", 500L, 3)),
            null, Money.ZERO));
        PaymentModel payment = new PaymentModel(order.getId(), USER_ID, CardType.SAMSUNG, order.getFinalAmount());
        payment.assignTransactionKey("tx-sold");
        paymentRepository.save(payment);

        // when
        paymentService.confirm("tx-sold", true, null);

        // then - 상품 2건에 대해 각각 발행되고, 타입/수량/키가 상품별로 맞다
        // 발행은 @Async라 timeout으로 대기한다. 같은 catalog-events에 다른 publisher(좋아요 등)의
        // 이벤트가 섞일 수 있으므로 PRODUCT_SOLD만 필터해 검증한다.
        verify(kafkaTemplate, timeout(3000).times(2)).send(eq(KafkaTopics.CATALOG_EVENTS), any(),
            argThat(v -> v instanceof CatalogEventPayload p && "PRODUCT_SOLD".equals(p.eventType())));

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, atLeast(2)).send(eq(KafkaTopics.CATALOG_EVENTS), any(), valueCaptor.capture());
        List<CatalogEventPayload> sold = valueCaptor.getAllValues().stream()
            .map(CatalogEventPayload.class::cast)
            .filter(p -> "PRODUCT_SOLD".equals(p.eventType()))
            .toList();
        assertThat(sold).hasSize(2);
        assertThat(sold).allSatisfy(p -> assertThat(p.eventId()).startsWith("sold-"));
        assertThat(sold)
            .extracting(CatalogEventPayload::productId, CatalogEventPayload::quantity)
            .containsExactlyInAnyOrder(tuple(100L, 2), tuple(200L, 3));
    }

    @DisplayName("판매 이벤트 발행이 실패해도 결제 확정은 롤백되지 않고 주문은 PAID로 유지된다(유실 허용)")
    @Test
    void keepsConfirm_whenPublishFails() {
        // given - catalog-events 발행이 실패 Future를 반환하도록
        CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq(KafkaTopics.CATALOG_EVENTS), any(), any())).thenReturn(failed);
        OrderModel order = orderRepository.save(new OrderModel(
            USER_ID, List.of(new OrderItem(100L, "상품-100", 1_000L, 1)), null, Money.ZERO));
        PaymentModel payment = new PaymentModel(order.getId(), USER_ID, CardType.SAMSUNG, order.getFinalAmount());
        payment.assignTransactionKey("tx-pub-fail");
        paymentRepository.save(payment);

        // when
        paymentService.confirm("tx-pub-fail", true, null);

        // then - 발행은 시도되지만 실패해도 결제/주문 확정은 유지된다
        verify(kafkaTemplate, timeout(3000)).send(eq(KafkaTopics.CATALOG_EVENTS), any(), any());
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
