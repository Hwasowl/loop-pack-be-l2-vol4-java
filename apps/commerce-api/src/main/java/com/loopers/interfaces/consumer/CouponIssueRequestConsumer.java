package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.confg.kafka.DlqPublisher;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.CouponIssueMessage;
import com.loopers.domain.coupon.CouponIssueOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * coupon-issue-requests 소비 → 선착순 발급. key=templateId 라 같은 템플릿 요청은 한 파티션에서 순차 처리된다.
 * 발급 로직 자체가 수량 조건부 UPDATE + 1인1매로 멱등하므로, 중복 전달돼도 안전(Inbox 불필요).
 * 결과는 로그로 남기고, 유저는 "내 쿠폰함"(GET /users/me/coupons)에서 발급 여부를 확인한다.
 * 테스트(브로커 없음)에선 coupon.issue-consumer!=kafka 로 비활성화된다.
 */
@Slf4j
@ConditionalOnProperty(name = "coupon.issue-consumer", havingValue = "kafka", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class CouponIssueRequestConsumer {

    private final CouponFacade couponFacade;
    private final ObjectMapper objectMapper;
    private final DlqPublisher dlqPublisher;

    @KafkaListener(
            topics = KafkaTopics.COUPON_ISSUE_REQUESTS,
            groupId = "coupon-issue-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                CouponIssueMessage message = objectMapper.readValue((byte[]) record.value(), CouponIssueMessage.class);
                CouponIssueOutcome outcome = couponFacade.issueFromRequest(
                        message.requestId(), message.userId(), message.couponTemplateId());
                log.info("[coupon-issue] request={}, user={}, template={} → {}",
                        message.requestId(), message.userId(), message.couponTemplateId(), outcome);
            } catch (Exception e) {
                // 역직렬화·처리 실패 메시지는 DLQ로 격리한다 — 파티션을 막지 않고 다음 메시지를 계속 처리한다.
                dlqPublisher.publish(KafkaTopics.COUPON_ISSUE_REQUESTS, record, e);
            }
        }
        acknowledgment.acknowledge();
    }
}
