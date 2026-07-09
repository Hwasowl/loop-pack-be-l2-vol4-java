package com.loopers.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.DlqMessage;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqReplayerTest {

    @Mock
    private ConsumerFactory<Object, Object> consumerFactory;
    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DlqReplayer replayer() {
        return new DlqReplayer(consumerFactory, kafkaTemplate, objectMapper);
    }

    @DisplayName("화이트리스트에 없는 토픽을 재처리하려 하면 BAD_REQUEST가 발생하고 컨슈머를 만들지 않는다")
    @Test
    void rejectsUnknownTopic() {
        assertThatThrownBy(() -> replayer().replay("arbitrary-topic"))
            .isInstanceOfSatisfying(CoreException.class, ex ->
                assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        verifyNoInteractions(consumerFactory);
    }

    @DisplayName("발행(send)이 실패하면 오프셋을 커밋하지 않아 다음 호출에서 재시도된다(커밋 전 유실 방지)")
    @Test
    void doesNotCommit_whenSendFails() throws Exception {
        // given - DLQ에서 한 건을 읽지만, 그 발행 Future가 실패로 완료된다
        Consumer<Object, Object> consumer = mock(Consumer.class);
        when(consumerFactory.createConsumer(anyString(), any())).thenReturn(consumer);

        byte[] dlqBytes = objectMapper.writeValueAsBytes(
            new DlqMessage(KafkaTopics.CATALOG_EVENTS, "100", "{\"eventType\":\"PRODUCT_SOLD\"}", "boom"));
        ConsumerRecord<Object, Object> record =
            new ConsumerRecord<>(KafkaTopics.dlq(KafkaTopics.CATALOG_EVENTS), 0, 0L, "100", dlqBytes);
        ConsumerRecords<Object, Object> records = new ConsumerRecords<>(
            Map.of(new TopicPartition(KafkaTopics.dlq(KafkaTopics.CATALOG_EVENTS), 0), List.of(record)));
        when(consumer.poll(any())).thenReturn(records);

        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        doReturn(failed).when(kafkaTemplate).send(anyString(), any(), any());

        // when & then - 발행 실패는 예외로 전파되고, 커밋은 일어나지 않는다
        assertThatThrownBy(() -> replayer().replay(KafkaTopics.CATALOG_EVENTS))
            .isInstanceOf(CoreException.class);
        verify(consumer, never()).commitSync();
    }
}
