package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderPaymentResultHandler;
import com.loopers.confg.kafka.DlqPublisher;
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
 * 테스트(브로커 없음)에서는 OrderPaymentEventListener(in-process)가 대신 처리한다 — payment.order-consumer 프로퍼티로 분기(@ConditionalOnProperty).
 * streamer의 기본 그룹과 섞이지 않도록 groupId를 명시한다.
 *
 * <p>유실 불허 토픽이라 offset 유실 상황(새 group.id, offsets 리텐션 만료)에서도 밀린 결제 이벤트를
 * 건너뛰지 않도록 이 리스너만 auto.offset.reset=earliest로 덮는다(전역 기본은 latest).
 * 상태 가드로 멱등이라 재처리해도 안전 — earliest가 유실을 막으면서 추가 비용이 없다.
 */
@Slf4j
@ConditionalOnProperty(name = "payment.order-consumer", havingValue = "kafka", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final OrderPaymentResultHandler handler;
    private final ObjectMapper objectMapper;
    private final DlqPublisher dlqPublisher;

    @KafkaListener(
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = "order-payment-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER,
            properties = {"auto.offset.reset=earliest"}
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                OrderEventMessage event = objectMapper.readValue((byte[]) record.value(), OrderEventMessage.class);
                switch (event.eventType()) {
                    case PAYMENT_COMPLETED -> handler.onPaid(event.orderId());
                    case PAYMENT_FAILED -> handler.onFailed(event.orderId());
                }
            } catch (Exception e) {
                // 역직렬화·처리 실패 메시지는 DLQ로 격리한다 — 파티션을 막지 않고 다음 메시지를 계속 처리한다.
                log.warn("[order-events] 처리 실패 — DLQ 격리 (offset={})", record.offset(), e);
                dlqPublisher.publish(KafkaTopics.ORDER_EVENTS, record, e);
            }
        }
        acknowledgment.acknowledge();
    }
}
