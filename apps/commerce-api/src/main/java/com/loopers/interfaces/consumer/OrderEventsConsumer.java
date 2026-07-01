package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderPaymentResultHandler;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.order.OrderEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * order-events(결제 확정) 소비 → 주문 반영. Outbox가 발행을 보장하고, 여기선 at-least-once로 받는다.
 * 핸들러가 주문 status 가드로 멱등하므로 중복 전달돼도 안전(Inbox 불필요).
 * 테스트(브로커 없음)에서는 OrderPaymentEventListener(in-process)가 대신 처리한다 — @Profile 분기.
 * streamer의 기본 그룹과 섞이지 않도록 groupId를 명시한다.
 */
@Slf4j
@ConditionalOnProperty(name = "payment.order-consumer", havingValue = "kafka", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final OrderPaymentResultHandler handler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = "order-payment-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            OrderEventMessage event = parse(record);
            if (event == null) {
                continue;
            }
            switch (event.eventType()) {
                case "PAYMENT_COMPLETED" -> handler.onPaid(event.orderId());
                case "PAYMENT_FAILED" -> handler.onFailed(event.orderId());
                default -> log.warn("알 수 없는 order 이벤트 타입 (offset={}): {}", record.offset(), event.eventType());
            }
        }
        acknowledgment.acknowledge();
    }

    private OrderEventMessage parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), OrderEventMessage.class);
        } catch (Exception e) {
            log.error("order-events 역직렬화 실패 (offset={}): {}", record.offset(), e.getMessage());
            return null;
        }
    }
}
