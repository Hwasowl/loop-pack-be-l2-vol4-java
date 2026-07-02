package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetrics;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMetricsServiceTest {

    @Mock
    private ProductMetricsRepository productMetricsRepository;
    @Mock
    private EventHandledRepository eventHandledRepository;

    private ProductMetricsService productMetricsService;

    @BeforeEach
    void setUp() {
        productMetricsService = new ProductMetricsService(productMetricsRepository, eventHandledRepository);
    }

    @DisplayName("판매 이벤트를 집계할 때")
    @Nested
    class ApplySold {

        @DisplayName("처음 도착한 판매 이벤트이면 수량만큼 salesCount를 올리고 event_handled에 기록한다")
        @Test
        void addsSalesAndRecordsEvent_whenFirstArrival() {
            // given
            when(eventHandledRepository.existsByEventId("sold-1")).thenReturn(false);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when
            productMetricsService.applySold("sold-1", 100L, 3);

            // then
            ArgumentCaptor<ProductMetrics> captor = ArgumentCaptor.forClass(ProductMetrics.class);
            verify(productMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getSalesCount()).isEqualTo(3L);
            verify(eventHandledRepository).save(any(EventHandled.class));
        }

        @DisplayName("이미 처리한 eventId이면 중복 집계하지 않고 아무 것도 저장하지 않는다")
        @Test
        void skips_whenAlreadyHandled() {
            // given
            when(eventHandledRepository.existsByEventId("sold-1")).thenReturn(true);

            // when
            productMetricsService.applySold("sold-1", 100L, 3);

            // then
            verify(productMetricsRepository, never()).save(any());
            verify(eventHandledRepository, never()).save(any());
        }
    }

    @DisplayName("좋아요 총량 스냅샷을 반영할 때")
    @Nested
    class ApplyLikeSnapshot {

        private static final ZonedDateTime T1 = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        private static final ZonedDateTime T2 = ZonedDateTime.parse("2026-01-01T00:00:01Z");

        @DisplayName("마지막 반영보다 최신인 스냅샷이면 like_count를 그 값으로 덮어쓴다")
        @Test
        void overwrites_whenNewer() {
            // given - 기존 행은 T1 시점의 스냅샷(5)을 갖고 있다
            ProductMetrics existing = ProductMetrics.init(100L);
            existing.applyLikeSnapshot(5L, T1);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // when - T2(더 최신) 스냅샷 7 도착
            productMetricsService.applyLikeSnapshot(100L, 7L, T2);

            // then
            verify(productMetricsRepository).save(existing);
            assertThat(existing.getLikeCount()).isEqualTo(7L);
        }

        @DisplayName("마지막 반영보다 오래된 스냅샷이면 무시하고 저장하지 않는다")
        @Test
        void skips_whenStale() {
            // given - 기존 행은 이미 T2 시점의 스냅샷(7)을 갖고 있다
            ProductMetrics existing = ProductMetrics.init(100L);
            existing.applyLikeSnapshot(7L, T2);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // when - 뒤늦게 도착한 T1(과거) 스냅샷 3
            productMetricsService.applyLikeSnapshot(100L, 3L, T1);

            // then - 최신값(7)이 과거값(3)으로 되돌아가지 않는다
            verify(productMetricsRepository, never()).save(any());
            assertThat(existing.getLikeCount()).isEqualTo(7L);
        }
    }
}
