package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.OrderEventMessage;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.payment.PaymentCompleted;
import com.loopers.domain.payment.PaymentFailed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 결제 확정 도메인 이벤트를 outbox에 적재한다.
 * @EventListener라 결제 상태 변경과 같은 트랜잭션에서 동기로 실행되어 원자적으로 커밋된다
 * (dual-write 갭 봉합 — 결제 커밋과 이벤트 적재가 한 번에 성공하거나 한 번에 롤백). 실제 발행은 OutboxRelay가 한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentOutboxAppender {

    static final String TYPE_COMPLETED = "PAYMENT_COMPLETED";
    static final String TYPE_FAILED = "PAYMENT_FAILED";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @EventListener
    public void on(PaymentCompleted event) {
        append(TYPE_COMPLETED, event.orderId());
    }

    @EventListener
    public void on(PaymentFailed event) {
        append(TYPE_FAILED, event.orderId());
    }

    private void append(String eventType, Long orderId) {
        outboxRepository.save(new OutboxEvent(orderId, eventType, serialize(eventType, orderId)));
    }

    private String serialize(String eventType, Long orderId) {
        try {
            return objectMapper.writeValueAsString(new OrderEventMessage(eventType, orderId));
        } catch (JsonProcessingException e) {
            // 직렬화 실패 시 언체크로 승격 → 결제 트랜잭션 롤백. 이벤트 없이 커밋돼 유실되는 것보다 낫다(재시도/복구가 다시 시도).
            throw new IllegalStateException("outbox 직렬화 실패: orderId=" + orderId, e);
        }
    }
}
