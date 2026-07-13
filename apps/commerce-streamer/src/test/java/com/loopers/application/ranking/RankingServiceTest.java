package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final ZonedDateTime T = ZonedDateTime.parse("2026-01-13T15:30:00+09:00");
    private static final Long PRODUCT_ID = 100L;

    @Mock
    private RankingRepository rankingRepository;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        // 가중치: 조회 0.1 / 좋아요 0.2 / 주문 0.6
        rankingService = new RankingService(rankingRepository, 0.1, 0.2, 0.6);
    }

    private double capturedDelta() {
        ArgumentCaptor<Double> captor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq(T), eq(PRODUCT_ID), captor.capture());
        return captor.getValue();
    }

    @DisplayName("조회 이벤트를 반영할 때")
    @Nested
    class ApplyView {

        @DisplayName("조회 1건은 조회 가중치(0.1)만큼 점수를 올린다")
        @Test
        void addsViewWeight() {
            // when
            rankingService.applyView(T, PRODUCT_ID);

            // then
            assertThat(capturedDelta()).isCloseTo(0.1, within(1e-9));
        }
    }

    @DisplayName("좋아요 델타를 반영할 때")
    @Nested
    class ApplyLikeDelta {

        @DisplayName("양(+)의 델타는 좋아요 가중치(0.2)를 곱해 점수를 올린다")
        @Test
        void addsWeightedPositiveDelta() {
            // when - 좋아요 3 증가
            rankingService.applyLikeDelta(T, PRODUCT_ID, 3L);

            // then - 0.2 * 3
            assertThat(capturedDelta()).isCloseTo(0.6, within(1e-9));
        }

        @DisplayName("언라이크로 인한 음(-)의 델타는 점수를 내린다")
        @Test
        void subtractsWeightedNegativeDelta() {
            // when - 좋아요 2 감소
            rankingService.applyLikeDelta(T, PRODUCT_ID, -2L);

            // then - 0.2 * -2
            assertThat(capturedDelta()).isCloseTo(-0.4, within(1e-9));
        }

        @DisplayName("델타가 0이면(스냅샷 미반영) ZSET을 건드리지 않는다")
        @Test
        void skips_whenZeroDelta() {
            // when
            rankingService.applyLikeDelta(T, PRODUCT_ID, 0L);

            // then
            verify(rankingRepository, never()).incrementScore(eq(T), eq(PRODUCT_ID), anyDouble());
        }
    }

    @DisplayName("주문 금액을 반영할 때")
    @Nested
    class ApplyOrder {

        @DisplayName("주문 금액(단가×수량)은 주문 가중치(0.6)를 곱해 점수를 올린다")
        @Test
        void addsWeightedAmount() {
            // when - 5만원 × 1개
            rankingService.applyOrder(T, PRODUCT_ID, 50_000L);

            // then - 0.6 * 50000
            assertThat(capturedDelta()).isCloseTo(30_000.0, within(1e-6));
        }

        @DisplayName("금액이 0이면(멱등 중복) ZSET을 건드리지 않는다")
        @Test
        void skips_whenZeroAmount() {
            // when
            rankingService.applyOrder(T, PRODUCT_ID, 0L);

            // then
            verify(rankingRepository, never()).incrementScore(eq(T), eq(PRODUCT_ID), anyDouble());
        }
    }
}
