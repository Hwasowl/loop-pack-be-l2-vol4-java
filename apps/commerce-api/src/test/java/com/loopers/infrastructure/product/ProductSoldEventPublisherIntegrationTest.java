package com.loopers.infrastructure.product;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
        // 발행은 @Async라 다른 스레드에서 일어나므로 timeout으로 완료를 기다린다.
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, timeout(3000).times(2)).send(eq(KafkaTopics.CATALOG_EVENTS), any(), valueCaptor.capture());

        List<CatalogEventPayload> payloads = valueCaptor.getAllValues().stream()
            .map(CatalogEventPayload.class::cast)
            .toList();
        assertThat(payloads).allSatisfy(p -> assertThat(p.eventType()).isEqualTo("PRODUCT_SOLD"));
        assertThat(payloads).allSatisfy(p -> assertThat(p.eventId()).startsWith("sold-"));
        assertThat(payloads)
            .extracting(CatalogEventPayload::productId, CatalogEventPayload::quantity)
            .containsExactlyInAnyOrder(tuple(100L, 2), tuple(200L, 3));
    }
}
