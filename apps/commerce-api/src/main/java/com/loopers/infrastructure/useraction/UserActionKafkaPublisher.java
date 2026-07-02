package com.loopers.infrastructure.useraction;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.useraction.UserActionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동 이벤트를 user-actions 토픽으로 발행한다.
 * 커밋 확정 후(AFTER_COMMIT)에만 발행해 롤백된 행동이 로그로 남지 않게 한다.
 * (트랜잭션 밖에서 발행되는 경우도 있으므로 fallbackExecution=true로 그때도 발행)
 * @Async라 요청 스레드와 완전히 분리 — 로깅 실패·지연이 본 기능(조회/좋아요/주문)을 건드리지 않는다.
 * 유실 허용이라 발행 실패는 로그만 남기고 넘어간다(Outbox 불필요). key=userId로 같은 유저 순서 보장(익명이면 null).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(UserActionEvent event) {
        try {
            String key = event.userId() == null ? null : event.userId().toString();
            kafkaTemplate.send(KafkaTopics.USER_ACTIONS, key, event);
        } catch (Exception e) {
            log.warn("유저 행동 로그 발행 실패 (action={}, targetId={}): {}", event.action(), event.targetId(), e.getMessage());
        }
    }
}
