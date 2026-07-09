package com.loopers.domain.outbox;

/**
 * 아웃박스 발행 상태.
 * PENDING(미발행 — 즉시발행/릴레이가 처리 대상) → PUBLISHED(발행 성공) / FAILED(반복 실패로 DLQ 격리, 종착).
 * FAILED는 재폴링에서 제외되면서도 성공(PUBLISHED)과 구분돼, 모니터링·수동 재구동(FAILED→PENDING)이 가능하다.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
