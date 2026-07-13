package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대기열 앞에서 batchSize 명을 꺼내 입장 토큰을 발급한다.
 * 100ms 주기 × 소량 배치로 발급을 분산해 Thundering Herd를 완화한다.
 * ⚠️ 멀티 인스턴스에서는 단일 실행 보장(분산락)이 필요하다. 현재는 단일 실행 전제.
 */
@Slf4j
@Profile("!test")
@Component
public class EntryTokenScheduler {

    private final QueueService queueService;
    private final EntryTokenService entryTokenService;

    // 근거: 커넥션 풀 40 / 200ms × 안전마진 70% = 140 TPS, 100ms 주기 → 14명/틱.
    private final int batchSize;

    public EntryTokenScheduler(
        QueueService queueService,
        EntryTokenService entryTokenService,
        @Value("${queue.scheduler.batch-size:14}") int batchSize
    ) {
        this.queueService = queueService;
        this.entryTokenService = entryTokenService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${queue.scheduler.fixed-delay-ms:100}")
    public void issueTokens() {
        try {
            List<Long> userIds = queueService.pollForEntry(batchSize);
            for (Long userId : userIds) {
                entryTokenService.issue(userId);
            }
        } catch (Exception e) {
            log.error("입장 토큰 발급 실패", e);
        }
    }
}
