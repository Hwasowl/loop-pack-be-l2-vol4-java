package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueService queueService;
    private final EntryTokenService entryTokenService;

    /**
     * 주문 관문 활성화 여부. 기본 false(우회) — 대기열 미적용 상태에서도 주문이 동작하고,
     * Redis 장애 시 우회(bypass) 정책의 스위치 역할도 겸한다. 행사 시 true로 켠다.
     */
    @Value("${queue.entry.enabled:false}")
    private boolean entryGateEnabled;

    public QueueInfo.Enter enter(Long userId) {
        long position = queueService.enter(userId);
        return new QueueInfo.Enter(position, queueService.waitingCount());
    }

    public QueueInfo.Position position(Long userId) {
        long position = queueService.position(userId);
        long estimatedWaitSeconds = queueService.estimatedWaitSeconds(position);
        String token = entryTokenService.peek(userId).orElse(null);
        return new QueueInfo.Position(position, estimatedWaitSeconds, token);
    }

    /** 주문 API 진입 검증. 게이트가 꺼져 있으면 통과, 켜져 있으면 토큰 검증 후 소비. */
    public void validateEntry(Long userId, String token) {
        if (!entryGateEnabled) {
            return;
        }
        entryTokenService.validateAndConsume(userId, token);
    }
}
