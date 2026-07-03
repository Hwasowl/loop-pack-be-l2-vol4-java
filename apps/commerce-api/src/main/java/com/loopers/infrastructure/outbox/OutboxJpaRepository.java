package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);

    /** 발행 완료 표시는 행별 짧은 트랜잭션으로 — 릴레이가 Kafka I/O를 트랜잭션 밖에서 하도록. */
    @Transactional
    @Modifying
    @Query("update OutboxEvent o set o.publishedAt = CURRENT_TIMESTAMP where o.id = :id")
    void markPublished(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("update OutboxEvent o set o.retryCount = o.retryCount + 1 where o.id = :id")
    void incrementRetryCount(@Param("id") Long id);
}
