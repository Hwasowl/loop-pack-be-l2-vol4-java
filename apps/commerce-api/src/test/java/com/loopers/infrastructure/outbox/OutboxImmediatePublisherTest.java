package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.payment.PaymentCompleted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxImmediatePublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private OutboxImmediatePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxImmediatePublisher(outboxRepository, kafkaTemplate, new ObjectMapper());
    }

    private OutboxEvent event(long orderId) {
        return new OutboxEvent(orderId, "PAYMENT_COMPLETED",
            "{\"eventType\":\"PAYMENT_COMPLETED\",\"orderId\":" + orderId + "}");
    }

    @DisplayName("커밋 확정 후 해당 주문의 미발행 아웃박스를 즉시 발행하고 발행 완료로 표시한다")
    @Test
    void publishesImmediately_onPaymentCompleted() {
        // given
        when(outboxRepository.findUnpublishedByAggregateId(1L)).thenReturn(List.of(event(1L)));
        CompletableFuture<SendResult<Object, Object>> ok = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(ok);

        // when
        publisher.on(new PaymentCompleted(1L));

        // then
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_EVENTS), eq("1"), any());
        verify(outboxRepository).markPublished(anyLong());
    }

    @DisplayName("즉시 발행이 실패하면 발행 완료로 표시하지 않고 릴레이 백스톱에 맡긴다")
    @Test
    void leavesForBackstop_onSendFailure() {
        // given
        when(outboxRepository.findUnpublishedByAggregateId(1L)).thenReturn(List.of(event(1L)));
        CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(failed);

        // when
        publisher.on(new PaymentCompleted(1L));

        // then - best-effort라 표시하지 않는다(릴레이 백스톱이 나중에 처리)
        verify(outboxRepository, never()).markPublished(anyLong());
    }
}
