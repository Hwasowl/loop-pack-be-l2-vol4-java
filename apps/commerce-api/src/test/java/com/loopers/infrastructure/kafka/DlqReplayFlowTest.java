package com.loopers.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.DlqMessage;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DLQ 재처리 검증(임베디드 Kafka) — {@code <topic>-dlq}에 격리된 메시지를 원본 토픽으로 되돌리는지 본다.
 * catalog-events는 소비자가 commerce-streamer에 있어 이 컨텍스트에는 없다 → 재발행분이 다시 DLQ로 튀지 않아
 * 재발행 결과와 "재replay 시 0건"을 깔끔히 검증할 수 있다. 실 consumer는 프로퍼티로 비활성화한다.
 */
@SpringBootTest(properties = {
    "payment.order-consumer=inprocess",
    "coupon.issue-consumer=inprocess",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.properties.auto.offset.reset=earliest"
})
@EmbeddedKafka(topics = {"catalog-events", "catalog-events-dlq"}, partitions = 1)
class DlqReplayFlowTest {

    @Autowired
    private DlqReplayer dlqReplayer;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("DLQ에 격리된 메시지를 재처리하면 원본 페이로드가 원본 토픽으로 재발행되고, 다시 호출하면 0건이다")
    @Test
    void replaysDlqMessageBackToOriginalTopic() throws Exception {
        // given - catalog-events-dlq에 원본 payload를 보존한 DlqMessage 한 건을 격리해 둔다
        String originalPayload = "{\"eventType\":\"PRODUCT_SOLD\",\"productId\":100,\"quantity\":3}";
        DlqMessage isolated = new DlqMessage(KafkaTopics.CATALOG_EVENTS, "100", originalPayload, "boom");
        kafkaTemplate.send(KafkaTopics.dlq(KafkaTopics.CATALOG_EVENTS), "100", isolated).get(5, TimeUnit.SECONDS);

        // when - 재처리
        int replayed = dlqReplayer.replay(KafkaTopics.CATALOG_EVENTS);

        // then - 1건 재발행되고, 원본 토픽에서 같은 내용이 관측된다
        assertThat(replayed).isEqualTo(1);

        Map<String, Object> props = KafkaTestUtils.consumerProps("replay-verifier", "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<byte[], byte[]> consumer = new DefaultKafkaConsumerFactory<>(
                props, new ByteArrayDeserializer(), new ByteArrayDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(KafkaTopics.CATALOG_EVENTS));
            ConsumerRecord<byte[], byte[]> record =
                KafkaTestUtils.getSingleRecord(consumer, KafkaTopics.CATALOG_EVENTS, Duration.ofSeconds(10));

            assertThat(new String(record.key())).contains("100");
            JsonNode payload = objectMapper.readTree(record.value());
            assertThat(payload.get("eventType").asText()).isEqualTo("PRODUCT_SOLD");
            assertThat(payload.get("productId").asLong()).isEqualTo(100L);
            assertThat(payload.get("quantity").asInt()).isEqualTo(3);
        }

        // and - 같은 group으로 커밋했으므로 다시 재처리하면 새로 쌓인 게 없어 0건이다(재replay 방지)
        assertThat(dlqReplayer.replay(KafkaTopics.CATALOG_EVENTS)).isZero();
    }
}
