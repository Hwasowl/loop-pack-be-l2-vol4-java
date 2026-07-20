package com.loopers.infrastructure.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RedisRankingRepositoryTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private RedisRankingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisRankingRepository(redisTemplate);
    }

    @DisplayName("Top-N을 조회할 때")
    @Nested
    class TopProductIds {

        @DisplayName("size가 0이면 Redis를 호출하지 않고 빈 목록을 반환한다")
        @Test
        void returnsEmpty_withoutRedisCall_whenSizeIsZero() {
            // when - stop 인덱스가 -1이 되어 ZREVRANGE key 0 -1(전체 조회)로 둔갑하는 경계
            List<Long> result = repository.topProductIds("ranking:all:20260113", 0, 0);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(redisTemplate);
        }

        @DisplayName("size가 음수여도 Redis를 호출하지 않고 빈 목록을 반환한다")
        @Test
        void returnsEmpty_withoutRedisCall_whenSizeIsNegative() {
            // when
            List<Long> result = repository.topProductIds("ranking:all:20260113", 0, -5);

            // then - Redis는 음수 인덱스를 "뒤에서 N번째"로 해석하므로 넘기면 안 된다
            assertThat(result).isEmpty();
            verifyNoInteractions(redisTemplate);
        }
    }
}
