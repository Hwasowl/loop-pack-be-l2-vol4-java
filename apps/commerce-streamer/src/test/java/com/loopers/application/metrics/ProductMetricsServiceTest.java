package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetrics;
import com.loopers.domain.productmetrics.ProductMetricsHourly;
import com.loopers.domain.productmetrics.ProductMetricsHourlyRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMetricsServiceTest {

    /**
     * Seoul 09:30 — 버킷은 09:00으로 절삭돼야 한다.
     * 존을 [Asia/Seoul]로 명시한다 — ZonedDateTime.equals()는 존까지 비교하므로,
     * 같은 순간이어도 +09:00 오프셋과 Asia/Seoul 존은 다른 값으로 취급된다.
     */
    private static final ZonedDateTime T = ZonedDateTime.parse("2026-01-13T09:30:00+09:00[Asia/Seoul]");
    private static final ZonedDateTime BUCKET = ZonedDateTime.parse("2026-01-13T09:00:00+09:00[Asia/Seoul]");

    @Mock
    private ProductMetricsRepository productMetricsRepository;
    @Mock
    private ProductMetricsHourlyRepository productMetricsHourlyRepository;
    @Mock
    private EventHandledRepository eventHandledRepository;

    private ProductMetricsService productMetricsService;

    /** 버킷이 없으면 새로 만들어 저장하는 경로. 조회·좋아요·판매 대부분의 테스트가 지나간다. */
    private ProductMetricsHourly newBucket;

    @BeforeEach
    void setUp() {
        productMetricsService = new ProductMetricsService(
                productMetricsRepository, productMetricsHourlyRepository, eventHandledRepository);
        newBucket = ProductMetricsHourly.init(100L, BUCKET, "SEARCH");
        lenient().when(productMetricsHourlyRepository.findByProductIdAndBucketHourAndSource(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(productMetricsHourlyRepository.save(any())).thenReturn(newBucket);
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
            productMetricsService.applySold("sold-1", 100L, 3, 150_000L, T);

            // then
            ArgumentCaptor<ProductMetrics> captor = ArgumentCaptor.forClass(ProductMetrics.class);
            verify(productMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getSalesCount()).isEqualTo(3L);
            verify(eventHandledRepository).save(any(EventHandled.class));
        }

        @DisplayName("처음 도착한 판매 이벤트이면 그 시간 버킷에 주문 금액이 누적된다")
        @Test
        void addsOrderAmountToBucket_whenFirstArrival() {
            // given
            when(eventHandledRepository.existsByEventId("sold-1")).thenReturn(false);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when
            productMetricsService.applySold("sold-1", 100L, 3, 150_000L, T);

            // then
            assertThat(newBucket.getOrderAmount()).isEqualTo(150_000L);
        }

        @DisplayName("이미 처리한 eventId이면 중복 집계하지 않고 버킷도 건드리지 않는다")
        @Test
        void skips_whenAlreadyHandled() {
            // given
            when(eventHandledRepository.existsByEventId("sold-1")).thenReturn(true);

            // when
            productMetricsService.applySold("sold-1", 100L, 3, 150_000L, T);

            // then - 멱등 장부가 현재 상태와 원천 사실을 함께 막는다
            verify(productMetricsRepository, never()).save(any());
            verify(eventHandledRepository, never()).save(any());
            assertThat(newBucket.getOrderAmount()).isZero();
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
            productMetricsService.applyLikeSnapshot(100L, 7L, 1L, T2);

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
            productMetricsService.applyLikeSnapshot(100L, 3L, -1L, T1);

            // then - 최신값(7)이 과거값(3)으로 되돌아가지 않는다
            verify(productMetricsRepository, never()).save(any());
            assertThat(existing.getLikeCount()).isEqualTo(7L);
        }

        @DisplayName("스냅샷이 오래돼 현재 상태에 반영되지 않아도 그 시간에 일어난 증감은 버킷에 기록된다")
        @Test
        void recordsBucketDelta_evenWhenSnapshotStale() {
            // given - 기존 행이 이미 더 최신(T2) 스냅샷을 갖고 있다
            ProductMetrics existing = ProductMetrics.init(100L);
            existing.applyLikeSnapshot(7L, T2);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // when - 뒤늦게 도착한 과거(T1) 이벤트
            productMetricsService.applyLikeSnapshot(100L, 3L, -1L, T1);

            // then - 현재 상태는 안 바뀌지만, 그때 좋아요가 취소된 사실 자체는 원천이므로 남긴다
            assertThat(newBucket.getLikeDelta()).isEqualTo(-1L);
        }

        @DisplayName("마지막 반영 시각과 동일한(이후가 아닌) 스냅샷이면 무시하고 저장하지 않는다")
        @Test
        void skips_whenEqualTimestamp() {
            // given - 기존 행이 T2 시점 스냅샷(7)을 갖고 있다
            ProductMetrics existing = ProductMetrics.init(100L);
            existing.applyLikeSnapshot(7L, T2);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // when - 같은 T2 시각의 스냅샷(9)이 재도착
            productMetricsService.applyLikeSnapshot(100L, 9L, 1L, T2);

            // then - eventAt이 '이후'가 아니라 '동일'이면 버린다(경계값)
            verify(productMetricsRepository, never()).save(any());
            assertThat(existing.getLikeCount()).isEqualTo(7L);
        }

        @DisplayName("좋아요 증감이 그 시간 버킷에 누적된다")
        @Test
        void addsLikeDeltaToBucket() {
            // given
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when
            productMetricsService.applyLikeSnapshot(100L, 7L, 1L, T);

            // then
            assertThat(newBucket.getLikeDelta()).isEqualTo(1L);
        }
    }

    @DisplayName("조회 수를 집계할 때")
    @Nested
    class ApplyView {

        @DisplayName("기존 metrics가 없으면 새로 생성해 view_count를 1 증가시킨다")
        @Test
        void addsView_whenNotExists() {
            // given
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when
            productMetricsService.applyView(100L, T, "SEARCH");

            // then
            ArgumentCaptor<ProductMetrics> captor = ArgumentCaptor.forClass(ProductMetrics.class);
            verify(productMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getViewCount()).isEqualTo(1L);
        }

        @DisplayName("기존 metrics가 있으면 그 view_count를 기존값+1로 증가시켜 저장한다")
        @Test
        void incrementsView_whenExists() {
            // given - 이미 4회 조회된 행이 있다
            ProductMetrics existing = ProductMetrics.init(100L);
            existing.addView(4L);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // when
            productMetricsService.applyView(100L, T, "SEARCH");

            // then - 조회 이벤트는 eventId가 없어 멱등 처리하지 않는다(append 집계) → 기존값+1
            verify(productMetricsRepository).save(existing);
            assertThat(existing.getViewCount()).isEqualTo(5L);
        }

        @DisplayName("조회가 그 시간 버킷에도 누적된다")
        @Test
        void addsViewToBucket() {
            // given
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when
            productMetricsService.applyView(100L, T, "SEARCH");

            // then
            assertThat(newBucket.getViewCount()).isEqualTo(1L);
        }
    }

    @DisplayName("버킷을 고를 때")
    @Nested
    class BucketSelection {

        @DisplayName("이벤트 발생 시각을 Asia/Seoul 정시로 절삭한 버킷에 기록한다")
        @Test
        void truncatesToSeoulHour() {
            // given
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when - Seoul 09:30에 발생
            productMetricsService.applyView(100L, T, "SEARCH");

            // then - 09:00 버킷을 찾는다
            verify(productMetricsHourlyRepository).findByProductIdAndBucketHourAndSource(100L, BUCKET, "SEARCH");
        }

        @DisplayName("UTC로 들어온 이벤트도 Asia/Seoul로 환산한 시간대 버킷에 기록한다")
        @Test
        void convertsUtcToSeoulBucket() {
            // given - UTC 00:30 = Seoul 09:30
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when
            productMetricsService.applyView(100L, ZonedDateTime.parse("2026-01-13T00:30:00Z"), "SEARCH");

            // then - Seoul 09:00 버킷과 같은 순간을 가리켜야 한다
            ArgumentCaptor<ZonedDateTime> captor = ArgumentCaptor.forClass(ZonedDateTime.class);
            verify(productMetricsHourlyRepository)
                    .findByProductIdAndBucketHourAndSource(any(), captor.capture(), any());
            assertThat(captor.getValue().toInstant()).isEqualTo(BUCKET.toInstant());
        }

        @DisplayName("이미 그 시간·경로 버킷이 있으면 새로 만들지 않고 기존 버킷에 누적한다")
        @Test
        void reusesExistingBucket() {
            // given - 이미 조회 4건이 쌓인 버킷이 있다
            ProductMetricsHourly existing = ProductMetricsHourly.init(100L, BUCKET, "SEARCH");
            existing.addView(4L);
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());
            when(productMetricsHourlyRepository.findByProductIdAndBucketHourAndSource(100L, BUCKET, "SEARCH"))
                    .thenReturn(Optional.of(existing));

            // when
            productMetricsService.applyView(100L, T, "SEARCH");

            // then
            verify(productMetricsHourlyRepository, never()).save(any());
            assertThat(existing.getViewCount()).isEqualTo(5L);
        }
    }

    @DisplayName("유입 경로를 나눌 때")
    @Nested
    class SourceSeparation {

        @DisplayName("같은 시간·상품이어도 유입 경로가 다르면 다른 버킷에 기록한다")
        @Test
        void separatesBucketBySource() {
            // given - 같은 09:00 버킷이지만 경로가 다르다
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when - 랭킹을 보고 들어온 조회
            productMetricsService.applyView(100L, T, "RANKING");

            // then - 랭킹 경유는 SEARCH와 갈라져야 나중에 "랭킹 경유는 빼고 재계산"이 가능하다
            verify(productMetricsHourlyRepository).findByProductIdAndBucketHourAndSource(100L, BUCKET, "RANKING");
        }

        @DisplayName("경로를 못 받은 조회는 UNKNOWN 버킷에 모은다")
        @Test
        void fallsBackToUnknown_whenSourceMissing() {
            // given
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when - 구버전 발행 측이 보낸, 경로 없는 조회
            productMetricsService.applyView(100L, T, null);

            // then - 버리지 않고 UNKNOWN으로 남긴다(사실은 기록하고 해석은 읽는 쪽이 한다)
            verify(productMetricsHourlyRepository).findByProductIdAndBucketHourAndSource(100L, BUCKET, "UNKNOWN");
        }

        @DisplayName("좋아요·주문은 경로가 없으므로 UNKNOWN 버킷에 기록한다")
        @Test
        void usesUnknown_forLikeAndOrder() {
            // given
            when(productMetricsRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // when
            productMetricsService.applyLikeSnapshot(100L, 7L, 1L, T);

            // then
            verify(productMetricsHourlyRepository).findByProductIdAndBucketHourAndSource(100L, BUCKET, "UNKNOWN");
        }
    }
}
