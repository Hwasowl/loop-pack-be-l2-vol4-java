package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxRepository {

    OutboxEvent save(OutboxEvent event);

    /** 미발행(published_at IS NULL) 행을 id 오름차순으로 최대 limit개 — 발행 순서 보장. */
    List<OutboxEvent> findUnpublished(int limit);

    void markPublished(Long id);
}
