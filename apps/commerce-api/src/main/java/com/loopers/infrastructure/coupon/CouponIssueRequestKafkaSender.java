package com.loopers.infrastructure.coupon;

import com.loopers.application.coupon.CouponIssueRequestSender;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.CouponIssueMessage;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 발급 요청을 coupon-issue-requests 토픽으로 발행한다. key=couponTemplateId 로 템플릿별 순차 처리를 보장한다.
 * 브로커 ack까지 기다려(.get()), 발행 실패는 조용히 삼키지 않고 호출자(발급요청 API)에 예외로 알린다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueRequestKafkaSender implements CouponIssueRequestSender {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public void send(String requestId, Long userId, Long couponTemplateId) {
        try {
            kafkaTemplate.send(
                    KafkaTopics.COUPON_ISSUE_REQUESTS,
                    couponTemplateId.toString(),
                    new CouponIssueMessage(requestId, userId, couponTemplateId)
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청 접수에 실패했습니다. 잠시 후 다시 시도해주세요.");
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청 접수에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
