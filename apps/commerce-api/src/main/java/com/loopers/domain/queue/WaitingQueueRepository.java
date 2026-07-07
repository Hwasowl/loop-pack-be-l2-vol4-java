package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface WaitingQueueRepository {

    /** 대기열 진입. 이미 있으면 최초 진입 시각을 유지한다(순번 앞당김 방지). */
    void enter(Long userId, long timestampMillis);

    /** 0-based 순번. 대기열에 없으면 empty. */
    Optional<Long> rank(Long userId);

    /** 전체 대기 인원. */
    long size();

    /** 앞에서 count 명을 원자적으로 꺼낸다(스케줄러). */
    List<Long> pollFirst(int count);
}
