package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisRankingRepository implements RankingRepository {

    // 갓 적재된 점수가 복제 지연으로 누락되면 순위가 어색해지므로 master 템플릿을 사용한다.
    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<Long> topProductIds(String key, long offset, long size) {
        // size가 0 이하면 stop 인덱스가 음수가 되고, Redis는 음수를 "뒤에서 N번째"로 해석한다.
        // 특히 offset=0·size=0이면 ZREVRANGE key 0 -1 — ZSET 전체를 끌어오는 명령이 된다.
        if (size <= 0) {
            return List.of();
        }
        // reverseRange 는 점수 내림차순 정렬된 LinkedHashSet 을 돌려주므로 순위 순서가 보존된다.
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key, offset, offset + size - 1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().map(RankingKeys::productIdOf).toList();
    }

    @Override
    public Optional<Long> rank(String key, Long productId) {
        return Optional.ofNullable(
                redisTemplate.opsForZSet().reverseRank(key, RankingKeys.member(productId)));
    }

    @Override
    public long size(String key) {
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }
}
