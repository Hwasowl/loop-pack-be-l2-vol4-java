package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.DlqPublisher;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Mock
    private DlqPublisher dlqPublisher;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate, new ObjectMapper(), dlqPublisher);
    }

    private OutboxEvent event(long orderId) {
        return new OutboxEvent(orderId, "PAYMENT_COMPLETED",
            "{\"eventType\":\"PAYMENT_COMPLETED\",\"orderId\":" + orderId + "}");
    }

    @DisplayName("미발행 아웃박스 행을 order-events로 발행하고 각 행을 발행 완료로 표시한다")
    @Test
    void publishesAndMarksEach() {
        // given
        when(outboxRepository.findUnpublished(anyInt())).thenReturn(List.of(event(1L), event(2L)));
        CompletableFuture<SendResult<Object, Object>> ok = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(ok);

        // when
        relay.relay();

        // then
        verify(kafkaTemplate, times(2)).send(anyString(), any(), any());
        verify(outboxRepository, times(2)).markPublished(anyLong());
    }

    @DisplayName("발행이 실패하면 그 행을 표시하지 않고 이후 행 처리를 중단한다(순서 보장)")
    @Test
    void stopsWithoutMarking_onSendFailure() {
        // given
        when(outboxRepository.findUnpublished(anyInt())).thenReturn(List.of(event(1L), event(2L)));
        CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(failed);

        // when
        relay.relay();

        // then - 표시하지 않고, 첫 실패에서 중단해 둘째 행은 발행 시도조차 안 한다(순서 보장)
        verify(outboxRepository, never()).markPublished(anyLong());
        verify(kafkaTemplate, times(1)).send(anyString(), any(), any());
    }

    @DisplayName("역직렬화 실패(포이즌) 행은 DLQ로 격리하고, 뒤의 정상 행은 계속 발행한다")
    @Test
    void routesPoisonToDlq_andContinues() {
        // given - 첫 행은 파싱 불가(포이즌), 둘째 행은 정상
        OutboxEvent poison = new OutboxEvent(9L, "PAYMENT_COMPLETED", "not-json");
        when(outboxRepository.findUnpublished(anyInt())).thenReturn(List.of(poison, event(2L)));
        CompletableFuture<SendResult<Object, Object>> ok = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(ok);

        // when
        relay.relay();

        // then - 포이즌은 DLQ로, 정상 행만 발행(포이즌이 뒤 행을 막지 않음)
        verify(dlqPublisher).publish(eq(KafkaTopics.ORDER_EVENTS), eq("9"), eq("not-json"), any());
        verify(kafkaTemplate, times(1)).send(anyString(), any(), any());
    }
}
