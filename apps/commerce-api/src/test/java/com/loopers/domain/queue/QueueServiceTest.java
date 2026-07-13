package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    private static final Long USER_ID = 10L;

    @Mock
    private WaitingQueueRepository waitingQueueRepository;

    @InjectMocks
    private QueueService queueService;

    @DisplayName("대기열 진입 시")
    @Nested
    class Enter {

        @DisplayName("진입 후 rank가 4이면 1-based 순번 5를 반환한다")
        @Test
        void returnsOneBasedPosition_afterEnter() {
            // given
            when(waitingQueueRepository.rank(USER_ID)).thenReturn(Optional.of(4L));

            // when
            long position = queueService.enter(USER_ID);

            // then
            assertThat(position).isEqualTo(5L);
            verify(waitingQueueRepository).enter(eq(USER_ID), anyLong());
        }
    }

    @DisplayName("순번 조회 시")
    @Nested
    class Position {

        @DisplayName("대기열에 있으면 rank + 1을 반환한다")
        @Test
        void returnsRankPlusOne_whenInQueue() {
            // given
            when(waitingQueueRepository.rank(USER_ID)).thenReturn(Optional.of(127L));

            // when
            long position = queueService.position(USER_ID);

            // then
            assertThat(position).isEqualTo(128L);
        }

        @DisplayName("대기열에 없으면 0을 반환한다")
        @Test
        void returnsZero_whenNotInQueue() {
            // given
            when(waitingQueueRepository.rank(USER_ID)).thenReturn(Optional.empty());

            // when
            long position = queueService.position(USER_ID);

            // then
            assertThat(position).isZero();
        }
    }

    @DisplayName("예상 대기 시간 계산 시")
    @Nested
    class EstimatedWaitSeconds {

        @DisplayName("순번이 0 이하이면 0초를 반환한다")
        @Test
        void returnsZero_whenPositionIsZeroOrLess() {
            assertThat(queueService.estimatedWaitSeconds(0)).isZero();
        }

        @DisplayName("순번을 목표 처리량으로 나눈 값을 올림해서 반환한다")
        @Test
        void returnsCeilOfPositionOverTps() {
            // given - TARGET_TPS = 140
            // when & then
            assertThat(queueService.estimatedWaitSeconds(140)).isEqualTo(1L);
            assertThat(queueService.estimatedWaitSeconds(141)).isEqualTo(2L);
            assertThat(queueService.estimatedWaitSeconds(280)).isEqualTo(2L);
        }
    }

    @DisplayName("스케줄러가 배치를 꺼낼 시")
    @Nested
    class PollForEntry {

        @DisplayName("배치 크기만큼 Repository의 pollFirst에 위임한다")
        @Test
        void delegatesToRepository() {
            // given
            when(waitingQueueRepository.pollFirst(14)).thenReturn(List.of(1L, 2L, 3L));

            // when
            List<Long> polled = queueService.pollForEntry(14);

            // then
            assertThat(polled).containsExactly(1L, 2L, 3L);
        }
    }
}
