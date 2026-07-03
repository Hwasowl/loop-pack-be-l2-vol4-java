package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 트랜잭셔널 아웃박스. 도메인 상태 변경과 "발행할 이벤트"를 같은 트랜잭션에 함께 커밋해
 * dual-write 갭(커밋 후 발행 전 유실)을 없앤다. Relay가 미발행 행을 폴링해 Kafka로 내보낸다.
 */
@Getter
@Entity
@Table(name = "outbox", indexes = @Index(name = "idx_outbox_status", columnList = "status, created_at, id"))
public class OutboxEvent extends BaseEntity {

    /** 파티션 키로 쓰는 애그리거트 식별자 (order-events는 orderId). */
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, length = 2000)
    private String payload;

    /** 발행 상태. PENDING(미발행) → PUBLISHED(발행성공) / FAILED(반복실패로 DLQ 격리). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    /** 발행(send) 실패 누적 횟수. 임계 초과 시 DLQ로 격리한다. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    protected OutboxEvent() {
    }

    public OutboxEvent(Long aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    /** 발행(send) 실패를 기록한다. */
    public void recordSendFailure() {
        this.retryCount++;
    }

    /** 발행 실패가 임계 횟수를 초과했는지 — 초과 시 DLQ로 격리한다. */
    public boolean sendFailureExceeded(int maxRetry) {
        return this.retryCount > maxRetry;
    }
}
