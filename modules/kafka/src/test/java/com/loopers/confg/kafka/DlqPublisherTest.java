package com.loopers.confg.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqPublisherTest {

    @Mock
    private org.springframework.kafka.core.KafkaTemplate<Object, Object> kafkaTemplate;

    @DisplayName("처리 실패 메시지를 원본 토픽의 -dlq 토픽으로 원본 페이로드·에러와 함께 재발행한다")
    @Test
    void publishesToDlqTopic_withOriginalPayloadAndError() {
        // given
        DlqPublisher dlqPublisher = new DlqPublisher(kafkaTemplate);
        CompletableFuture<SendResult<Object, Object>> ok = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(ok);
        byte[] value = "{\"eventType\":\"X\",\"orderId\":42}".getBytes(StandardCharsets.UTF_8);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("order-events", 0, 5L, "42", value);

        // when
        dlqPublisher.publish("order-events", record, new RuntimeException("boom"));

        // then
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("order-events-dlq"), eq("42"), valueCaptor.capture());
        DlqMessage sent = (DlqMessage) valueCaptor.getValue();
        assertThat(sent.originalTopic()).isEqualTo("order-events");
        assertThat(sent.key()).isEqualTo("42");
        assertThat(sent.payload()).contains("\"orderId\":42");
        assertThat(sent.error()).isEqualTo("boom");
    }

    @DisplayName("DLQ 재발행마저 실패해도 예외를 밖으로 던지지 않는다")
    @Test
    void doesNotThrow_whenDlqSendFails() {
        // given
        DlqPublisher dlqPublisher = new DlqPublisher(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), any(), any())).thenThrow(new RuntimeException("dlq down"));
        byte[] value = "payload".getBytes(StandardCharsets.UTF_8);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("order-events", 0, 5L, "42", value);

        // when & then - 내부에서 삼켜 파티션을 막지 않는다
        assertThatCode(() -> dlqPublisher.publish("order-events", record, new RuntimeException("boom")))
            .doesNotThrowAnyException();
        verify(kafkaTemplate).send(eq("order-events-dlq"), eq("42"), any());
    }
}
