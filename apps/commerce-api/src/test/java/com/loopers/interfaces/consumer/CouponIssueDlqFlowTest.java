package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.DlqMessage;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DLQ 배선 검증(임베디드 Kafka) — 역직렬화 불가한 메시지가 파티션을 막지 않고 -dlq 토픽으로 격리되는지 본다.
 */
@SpringBootTest(properties = {
    "coupon.issue-consumer=kafka",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.properties.auto.offset.reset=earliest"
})
@EmbeddedKafka(topics = {"coupon-issue-requests", "coupon-issue-requests-dlq"}, partitions = 1)
class CouponIssueDlqFlowTest {

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

    @DisplayName("역직렬화할 수 없는 메시지는 coupon-issue-requests-dlq로 격리된다")
    @Test
    void routesPoisonMessageToDlq() throws Exception {
        // given - CouponIssueMessage로 매핑 불가한 페이로드(JSON 문자열)
        kafkaTemplate.send("coupon-issue-requests", "1", "poison-not-an-object").get();

        // when - DLQ 토픽을 구독해 격리된 메시지를 읽는다
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-verifier", "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<byte[], byte[]> consumer = new DefaultKafkaConsumerFactory<>(
                props, new ByteArrayDeserializer(), new ByteArrayDeserializer()).createConsumer()) {
            consumer.subscribe(java.util.List.of("coupon-issue-requests-dlq"));

            ConsumerRecord<byte[], byte[]> record =
                KafkaTestUtils.getSingleRecord(consumer, "coupon-issue-requests-dlq", Duration.ofSeconds(10));

            // then
            DlqMessage dlq = objectMapper.readValue(record.value(), DlqMessage.class);
            assertThat(dlq.originalTopic()).isEqualTo("coupon-issue-requests");
            assertThat(dlq.error()).isNotBlank();
        }
    }
}
