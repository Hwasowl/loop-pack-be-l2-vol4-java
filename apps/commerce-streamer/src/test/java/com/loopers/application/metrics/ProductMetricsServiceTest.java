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
}
