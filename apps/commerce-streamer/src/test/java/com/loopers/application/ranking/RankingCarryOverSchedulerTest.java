package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingCarryOverSchedulerTest {

    @Mock
    private RankingRepository rankingRepository;

    @DisplayName("Carry-Over를 실행하면")
    @Test
    void carriesOverTodayToTomorrow_withConfiguredWeight() {
        // given
        RankingCarryOverScheduler scheduler = new RankingCarryOverScheduler(rankingRepository, 0.1);

        // when
        scheduler.carryOverToTomorrow();

        // then - 오늘 → 내일, 설정 가중치로 이월한다
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(rankingRepository).carryOver(from.capture(), to.capture(), eq(0.1));
        assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(1));
    }

    @DisplayName("이월 중 예외가 나도")
    @Test
    void swallowsException_soSchedulerNeverPropagates() {
        // given - 이월이 실패하도록 설정
        doThrow(new RuntimeException("redis down"))
                .when(rankingRepository).carryOver(any(), any(), anyDouble());
        RankingCarryOverScheduler scheduler = new RankingCarryOverScheduler(rankingRepository, 0.1);

        // when & then - 예외를 삼켜 스케줄러 밖으로 던지지 않는다
        assertThatCode(scheduler::carryOverToTomorrow).doesNotThrowAnyException();
    }
}
