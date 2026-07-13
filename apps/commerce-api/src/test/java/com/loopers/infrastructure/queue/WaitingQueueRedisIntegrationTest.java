package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitingQueueRedisIntegrationTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("동시 진입 시")
    @Nested
    class ConcurrentEnter {

        @DisplayName("100명이 동시에 진입해도 유실 없이 진입 시각(score) 순서대로 꺼내진다")
        @Test
        void preservesScoreOrder_underConcurrentEnter() throws InterruptedException {
            // given - 진입 시각(score)을 userId 로 고정해 도착 순서를 결정적으로 만든다
            int userCount = 100;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(userCount);
            ExecutorService executor = Executors.newFixedThreadPool(32);

            // when - 모든 유저가 동시에 진입 (스레드 스케줄링과 무관하게 score = userId)
            for (long userId = 1; userId <= userCount; userId++) {
                long id = userId;
                executor.submit(() -> {
                    try {
                        start.await();
                        waitingQueueRepository.enter(id, id);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            try {
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            // then - 동시 ZADD에서 유실 없이 100명, score 순서대로 꺼내진다
            assertThat(waitingQueueRepository.size()).isEqualTo(userCount);
            List<Long> polled = waitingQueueRepository.pollFirst(userCount);
            assertThat(polled).containsExactlyElementsOf(
                LongStream.rangeClosed(1, userCount).boxed().toList()
            );
        }
    }

    @DisplayName("중복 진입 시")
    @Nested
    class DuplicateEnter {

        @DisplayName("같은 유저가 다시 진입해도 최초 진입 시각이 유지되어 순번이 앞당겨지지 않는다")
        @Test
        void keepsFirstEntryTime_onReenter() {
            // given - 1,2,3 진입 후 1번이 늦은 시각으로 재진입
            waitingQueueRepository.enter(1L, 100L);
            waitingQueueRepository.enter(2L, 200L);
            waitingQueueRepository.enter(3L, 300L);

            // when
            waitingQueueRepository.enter(1L, 999L);

            // then - 인원은 그대로 3명, 1번은 여전히 맨 앞(rank 0)
            assertThat(waitingQueueRepository.size()).isEqualTo(3L);
            assertThat(waitingQueueRepository.rank(1L)).contains(0L);
        }
    }

    @DisplayName("스케줄러가 동시에 꺼낼 시")
    @Nested
    class ConcurrentPoll {

        @DisplayName("두 스케줄러가 동시에 ZPOPMIN을 호출해도 같은 유저를 중복으로 꺼내지 않는다")
        @Test
        void neverPopsSameUserTwice_underConcurrentPoll() throws InterruptedException {
            // given - 100명 진입
            int userCount = 100;
            for (long userId = 1; userId <= userCount; userId++) {
                waitingQueueRepository.enter(userId, userId);
            }

            // when - 두 스케줄러가 각각 50명씩 동시에 꺼냄
            Set<Long> collected = ConcurrentHashMap.newKeySet();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        collected.addAll(waitingQueueRepository.pollFirst(50));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            try {
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            // then - 중복 없이 정확히 100명, 대기열은 비었다
            assertThat(collected).hasSize(userCount);
            assertThat(waitingQueueRepository.size()).isZero();
        }
    }
}
