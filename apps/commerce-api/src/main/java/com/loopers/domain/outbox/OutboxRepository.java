package com.loopers.domain.outbox;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxRepository {

    OutboxEvent save(OutboxEvent event);

    /** 백스톱 폴링: 미발행 + 생성 후 grace 경과(즉시발행이 못 채간 낙오분)만 id 오름차순 최대 limit개. */
    List<OutboxEvent> findUnpublishedOlderThan(ZonedDateTime threshold, int limit);

    /** 즉시발행: 특정 애그리거트의 미발행 행을 id 오름차순으로. */
    List<OutboxEvent> findUnpublishedByAggregateId(Long aggregateId);

    void markPublished(Long id);

    /** 발행 실패 시 재시도 횟수를 1 증가시킨다(임계 초과 판정용). */
    void incrementRetryCount(Long id);
}
