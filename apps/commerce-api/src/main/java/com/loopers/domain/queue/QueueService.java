package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class QueueService {

    /**
     * 목표 처리량(TPS). 예상 대기 시간 계산의 기준값.
     * 근거: 커넥션 풀 40 / 주문 200ms = 200 TPS × 안전마진 70% = 140.
     */
    public static final int TARGET_TPS = 140;

    private final WaitingQueueRepository waitingQueueRepository;

    /** 대기열 진입 후 1-based 순번을 반환한다. */
    public long enter(Long userId) {
        waitingQueueRepository.enter(userId, System.currentTimeMillis());
        return position(userId);
    }

    /** 1-based 순번. 대기열에 없으면 0. */
    public long position(Long userId) {
        return waitingQueueRepository.rank(userId)
            .map(rank -> rank + 1)
            .orElse(0L);
    }

    public long waitingCount() {
        return waitingQueueRepository.size();
    }

    /** 예상 대기 시간(초). position ÷ 목표 처리량 올림. 추정값. */
    public long estimatedWaitSeconds(long position) {
        if (position <= 0) {
            return 0;
        }
        return (long) Math.ceil((double) position / TARGET_TPS);
    }

    /** 스케줄러가 앞에서 batchSize 명을 원자적으로 꺼낸다. */
    public List<Long> pollForEntry(int batchSize) {
        return waitingQueueRepository.pollFirst(batchSize);
    }
}
