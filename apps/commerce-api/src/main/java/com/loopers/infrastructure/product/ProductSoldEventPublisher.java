package com.loopers.infrastructure.product;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.PaymentCompleted;
import com.loopers.infrastructure.like.CatalogEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;

/**
 * 결제 완료 후 주문의 상품별 판매 이벤트(PRODUCT_SOLD)를 catalog-events 토픽으로 발행한다.
 * 결제가 커밋된 뒤(AFTER_COMMIT)에만 "팔린 것"으로 보고, 라인아이템마다 하나씩 쪼개 발행한다(key=productId).
 * eventId는 order_item.id 기반 결정적 키라 재전달돼도 소비 측 event_handled로 멱등 처리된다.
 * 판매량 집계는 파생 지표(원본=주문/결제 RDB)라 유실을 허용하고 Outbox 없이 직접 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSoldEventPublisher {

    private static final String TYPE_SOLD = "PRODUCT_SOLD";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void on(PaymentCompleted event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            String occurredAt = ZonedDateTime.now().toString();
            for (OrderItem item : order.getItems()) {
                publish(item, occurredAt);
            }
        });
    }

    private void publish(OrderItem item, String occurredAt) {
        CatalogEventPayload payload = new CatalogEventPayload(
                "sold-" + item.getId(), TYPE_SOLD, item.getProductId(), null, occurredAt, item.getQuantity());
        try {
            kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, item.getProductId().toString(), payload);
        } catch (Exception e) {
            // 결제는 이미 커밋됐다. 발행 실패로 결제 확정을 되돌리지 않고 집계 이벤트만 버린다(유실 허용).
            log.warn("판매 이벤트 발행 실패 (productId={}, orderItemId={}): {}", item.getProductId(), item.getId(), e.getMessage());
        }
    }
}
