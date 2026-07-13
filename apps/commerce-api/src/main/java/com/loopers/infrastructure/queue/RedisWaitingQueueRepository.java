package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String KEY = "queue:waiting";

    // 순번/인원 조회가 복제 지연으로 stale하면 안 되므로 master 템플릿을 사용한다.
    private final RedisTemplate<String, String> redisTemplate;

    public RedisWaitingQueueRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void enter(Long userId, long timestampMillis) {
        // addIfAbsent = ZADD NX: 재진입해도 최초 진입 시각(score)을 유지한다.
        redisTemplate.opsForZSet().addIfAbsent(KEY, String.valueOf(userId), timestampMillis);
    }

    @Override
    public Optional<Long> rank(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(KEY, String.valueOf(userId)));
    }

    @Override
    public long size() {
        Long count = redisTemplate.opsForZSet().zCard(KEY);
        return count == null ? 0L : count;
    }

    @Override
    public List<Long> pollFirst(int count) {
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(KEY, count);
        if (popped == null || popped.isEmpty()) {
            return List.of();
        }
        return popped.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Long::valueOf)
            .toList();
    }
}
