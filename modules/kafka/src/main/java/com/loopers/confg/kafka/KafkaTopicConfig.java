package com.loopers.confg.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * 토픽·파티션 수를 코드로 고정한다. 브로커 auto-create 디폴트(num.partitions=1)에 맡기면
 * 키 기반으로 병렬 처리하려던 설계가 실제로는 단일 파티션 직렬로 떨어지므로, 여기서 명시한다.
 * KafkaAdmin이 시작 시 없는 토픽은 생성하고, 파티션이 부족하면 늘린다(줄이지는 않음).
 *
 * <p>파티션 수 결정:
 * <ul>
 *   <li>메인 토픽 = {@value #KEYED_PARTITIONS} — 서로 다른 key(productId/orderId/couponId/userId)를
 *       파티션에 분산해 병렬 소비. consumer concurrency(3)와 맞춘다.</li>
 *   <li>DLQ = {@value #DLQ_PARTITIONS} — 격리·수동 재처리용이라 저볼륨. 순서/병렬 이점이 필요 없다.</li>
 * </ul>
 * replication-factor는 단일 브로커(dev) 기준 1. 다중 브로커 전환 시 3 + min.insync.replicas=2로 올린다.
 *
 * <p>주의: 이미 데이터가 쌓인 keyed 토픽의 파티션 수를 나중에 바꾸면 key→partition 매핑이 달라져
 * 기존 키의 순서 보장이 깨진다 — 파티션 수는 지금(dev) 확정한다.
 */
@Configuration
public class KafkaTopicConfig {

    private static final int KEYED_PARTITIONS = 3;
    private static final int DLQ_PARTITIONS = 1;
    private static final short REPLICATION_FACTOR = 1;

    @Bean
    public KafkaAdmin.NewTopics topics() {
        return new KafkaAdmin.NewTopics(
            keyed(KafkaTopics.CATALOG_EVENTS),
            keyed(KafkaTopics.ORDER_EVENTS),
            keyed(KafkaTopics.COUPON_ISSUE_REQUESTS),
            keyed(KafkaTopics.USER_ACTIONS),
            dlq(KafkaTopics.CATALOG_EVENTS),
            dlq(KafkaTopics.ORDER_EVENTS),
            dlq(KafkaTopics.COUPON_ISSUE_REQUESTS),
            dlq(KafkaTopics.USER_ACTIONS)
        );
    }

    private NewTopic keyed(String name) {
        return TopicBuilder.name(name).partitions(KEYED_PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }

    private NewTopic dlq(String topic) {
        return TopicBuilder.name(KafkaTopics.dlq(topic)).partitions(DLQ_PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }
}
