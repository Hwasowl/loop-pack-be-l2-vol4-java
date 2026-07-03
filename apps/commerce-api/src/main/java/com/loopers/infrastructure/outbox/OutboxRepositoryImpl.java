package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

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
    public List<OutboxEvent> findUnpublished(int limit) {
        return outboxJpaRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, limit));
    }

    @Override
    public void markPublished(Long id) {
        outboxJpaRepository.markPublished(id);
    }

    @Override
    public void incrementRetryCount(Long id) {
        outboxJpaRepository.incrementRetryCount(id);
    }
}
