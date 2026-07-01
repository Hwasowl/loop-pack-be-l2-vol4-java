package com.loopers.application.order;

import com.loopers.domain.payment.PaymentCompleted;
import com.loopers.domain.payment.PaymentFailed;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 테스트 전용 in-process 결제 반영 경로. 운영(!test)에서는 Outbox→Kafka→OrderEventsConsumer가 대신 처리한다.
 * 브로커 없는 H2 통합 테스트에서 결제 확정 흐름을 그대로 검증하기 위해 남겨둔다(핸들러는 두 경로가 공유).
 */
@ConditionalOnProperty(name = "payment.order-consumer", havingValue = "in-process")
@RequiredArgsConstructor
@Component
public class OrderPaymentEventListener {

    private final OrderPaymentResultHandler handler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentCompleted event) {
        handler.onPaid(event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentFailed event) {
        handler.onFailed(event.orderId());
    }
}
