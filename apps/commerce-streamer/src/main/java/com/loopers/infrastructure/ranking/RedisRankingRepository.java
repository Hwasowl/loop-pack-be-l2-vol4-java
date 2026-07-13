package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@Component
public class RedisRankingRepository implements RankingRepository {

    // 갓 반영한 점수를 곧바로 조회해야 하므로 master 템플릿(복제 지연 회피).
    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(ZonedDateTime occurredAt, Long productId, double delta) {
        String member = RankingKeys.member(productId);
        applyTo(RankingKeys.daily(occurredAt), member, delta, RankingKeys.dailyExpiry(occurredAt));
        applyTo(RankingKeys.hourly(occurredAt), member, delta, RankingKeys.hourlyExpiry(occurredAt));
    }

    private void applyTo(String key, String member, double delta, Instant expiry) {
        redisTemplate.opsForZSet().incrementScore(key, member, delta);
        // ZINCRBY는 TTL을 갱신하지 않으므로 버킷 종료 기준 절대 만료를 건다(매 쓰기 동일 값이라 드리프트 없음).
        redisTemplate.expireAt(key, Date.from(expiry));
    }

    @Override
    public void carryOver(LocalDate from, LocalDate to, double weight) {
        String fromKey = RankingKeys.dailyKey(from);
        String toKey = RankingKeys.dailyKey(to);
        // ZUNIONSTORE toKey 1 fromKey WEIGHTS weight AGGREGATE SUM — 전날 점수에 작은 가중치를 곱해 이월한다.
        redisTemplate.opsForZSet().unionAndStore(fromKey, List.of(), toKey, Aggregate.SUM, Weights.of(weight));
        redisTemplate.expireAt(toKey, Date.from(RankingKeys.dailyExpiry(to)));
    }
}
