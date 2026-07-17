package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetrics;
import com.loopers.domain.productmetrics.ProductMetricsHourly;
import com.loopers.domain.productmetrics.ProductMetricsHourlyRepository;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 이벤트를 소비해 RDB에 두 가지를 남긴다 — <b>현재 상태</b>(product_metrics)와
 * <b>시간별 원천 사실</b>(product_metrics_hourly, SOT).
 * <p>
 * 둘은 같은 트랜잭션에서 함께 반영한다. 한쪽만 커밋되면 "현재 좋아요는 10개인데 그 10개가 언제
 * 생겼는지는 모르는" 상태가 되어, SOT로 랭킹을 재구성할 때 조용히 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final EventHandledRepository eventHandledRepository;

    /**
     * 좋아요 총량 스냅샷을 소비해 product_metrics.like_count에 최신-우선으로 반영하고,
     * 그 시간대 버킷에 증감(likeDelta)을 누적한다.
     * <p>
     * 절대값 덮어쓰기라 중복·유실엔 강하고(다음 스냅샷이 교정), 순서 역전만 occurredAt 비교로 막는다.
     * 그래서 멱등 장부(event_handled)가 필요 없다 — 오래된 이벤트는 모델이 스스로 버린다.
     * 단, 오래된 스냅샷이라 현재 상태에 반영되지 않았더라도 <b>그 시간에 좋아요가 눌린 사실 자체는</b>
     * 유효하므로 버킷에는 기록한다.
     */
    @Transactional
    public void applyLikeSnapshot(Long productId, long likeCount, long likeDelta, ZonedDateTime occurredAt) {
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        if (metrics.applyLikeSnapshot(likeCount, occurredAt)) {
            productMetricsRepository.save(metrics);
        }
        bucketOf(productId, occurredAt).addLikeDelta(likeDelta);
    }

    /**
     * 조회 이벤트를 소비해 view_count를 1 증가시키고, 그 시간대 버킷에도 누적한다.
     * 조회 수는 인기 지표라 소량의 중복 집계를 허용한다 — 매 조회 eventId를 event_handled에 남기면
     * (조회량 ≫ 좋아요량) 그 테이블이 폭증하므로 멱등 처리를 생략한다(at-least-once 근사 집계).
     */
    @Transactional
    public void applyView(Long productId, ZonedDateTime occurredAt) {
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        metrics.addView(1L);
        productMetricsRepository.save(metrics);
        bucketOf(productId, occurredAt).addView(1L);
    }

    /**
     * 판매 이벤트를 소비해 product_metrics.sales_count에 수량을, 그 시간대 버킷에 금액(단가×수량)을 더한다.
     * 판매량은 조회와 달리 이중집계에 민감하므로 eventId(주문 라인 기반 결정적 키)로 멱등 처리한다.
     * 멱등 장부가 둘을 함께 막으므로 버킷도 이중 가산되지 않는다.
     */
    @Transactional
    public void applySold(String eventId, Long productId, int quantity, long amount, ZonedDateTime occurredAt) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return;
        }
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        metrics.addSales(quantity);
        productMetricsRepository.save(metrics);
        eventHandledRepository.save(new EventHandled(eventId));
        bucketOf(productId, occurredAt).addOrderAmount(amount);
    }

    /**
     * 이벤트 발생 시각이 속한 시간 버킷을 가져온다(없으면 만든다).
     * 반환한 엔티티는 영속 상태라 호출부의 변경이 dirty checking으로 반영된다.
     */
    private ProductMetricsHourly bucketOf(Long productId, ZonedDateTime occurredAt) {
        ZonedDateTime bucketHour = occurredAt.withZoneSameInstant(SEOUL).truncatedTo(ChronoUnit.HOURS);
        return productMetricsHourlyRepository.findByProductIdAndBucketHour(productId, bucketHour)
                .orElseGet(() -> productMetricsHourlyRepository.save(
                        ProductMetricsHourly.init(productId, bucketHour)));
    }
}
