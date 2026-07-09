package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return outboxJpaRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findUnpublishedOlderThan(ZonedDateTime threshold, int limit) {
        return outboxJpaRepository.findByStatusAndCreatedAtLessThanOrderByIdAsc(OutboxStatus.PENDING, threshold, PageRequest.of(0, limit));
    }

    @Override
    public List<OutboxEvent> findUnpublishedByAggregateId(Long aggregateId) {
        return outboxJpaRepository.findByStatusAndAggregateIdOrderByIdAsc(OutboxStatus.PENDING, aggregateId);
    }

    @Override
    public void markPublished(Long id) {
        outboxJpaRepository.updateStatus(id, OutboxStatus.PUBLISHED);
    }

    @Override
    public void markFailed(Long id) {
        outboxJpaRepository.updateStatus(id, OutboxStatus.FAILED);
    }

    @Override
    public void incrementRetryCount(Long id) {
        outboxJpaRepository.incrementRetryCount(id);
    }
}
