package com.loopers.interfaces.consumer;

/** user-actions 토픽 메시지. commerce-api의 UserActionEvent와 필드가 대응한다. */
public record UserActionMessage(Long userId, String action, Long targetId, String occurredAt) {
}
