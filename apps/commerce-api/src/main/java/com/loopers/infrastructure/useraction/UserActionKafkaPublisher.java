package com.loopers.infrastructure.useraction;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.useraction.UserActionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 유저 행동 이벤트를 user-actions 토픽으로 발행한다.
 * @Async라 요청 스레드와 완전히 분리 — 로깅 실패·지연이 본 기능(조회/좋아요/주문)을 건드리지 않는다.
 * 유실 허용이라 발행 실패는 로그만 남기고 넘어간다(Outbox 불필요). key=userId로 같은 유저 순서 보장(익명이면 null).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Async
    @EventListener
    public void on(UserActionEvent event) {
        try {
            String key = event.userId() == null ? null : event.userId().toString();
            kafkaTemplate.send(KafkaTopics.USER_ACTIONS, key, event);
        } catch (Exception e) {
            log.warn("유저 행동 로그 발행 실패 (action={}, targetId={}): {}", event.action(), event.targetId(), e.getMessage());
        }
    }
}
