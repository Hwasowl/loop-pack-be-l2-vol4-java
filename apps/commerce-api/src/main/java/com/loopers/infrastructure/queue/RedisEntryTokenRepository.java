package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisEntryTokenRepository implements EntryTokenRepository {

    private static final String KEY_PREFIX = "queue:token:";

    // 발급 직후의 토큰 조회가 복제 지연으로 누락되면 안 되므로 master 템플릿을 사용한다.
    private final RedisTemplate<String, String> redisTemplate;

    public RedisEntryTokenRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void issue(Long userId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), token, ttl);
    }

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public boolean delete(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.delete(key(userId)));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
