package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusAndCreatedAtLessThanOrderByIdAsc(OutboxStatus status, ZonedDateTime threshold, Pageable pageable);

    List<OutboxEvent> findByStatusAndAggregateIdOrderByIdAsc(OutboxStatus status, Long aggregateId);

    /** 상태 전이는 행별 짧은 트랜잭션으로 — 릴레이가 Kafka I/O를 트랜잭션 밖에서 하도록. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OutboxEvent o set o.status = :status where o.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") OutboxStatus status);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OutboxEvent o set o.retryCount = o.retryCount + 1 where o.id = :id")
    void incrementRetryCount(@Param("id") Long id);
}
