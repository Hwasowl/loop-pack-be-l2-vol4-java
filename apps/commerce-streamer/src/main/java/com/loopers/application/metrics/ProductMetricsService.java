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

    /** 유입 경로가 없는 이벤트(좋아요·주문)와, 경로를 못 받은 조회가 모이는 값. */
    private static final String SOURCE_UNKNOWN = "UNKNOWN";

    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final EventHandledRepository eventHandledRepository;

    /**
     * 좋아요 총량 스냅샷을 소비해 product_metrics.like_count에 최신-우선으로 반영하고,
     * 그 시간대 버킷에 증감(likeDelta)을 누적한다.
     * <p>
     * 현재 상태는 절대값 덮어쓰기라 중복·유실에 강하다(다음 스냅샷이 교정). 순서 역전만 occurredAt 비교로 막는다.
     * 단, 오래된 스냅샷이라 현재 상태에 반영되지 않았더라도 <b>그 시간에 좋아요가 눌린 사실 자체는</b>
     * 유효하므로 버킷에는 기록한다.
     * <p>
     * 버킷은 절대값이 아니라 <b>증감의 누적</b>이라 중복에 강하지 않다 — 같은 이벤트를 두 번 받으면 +1이 +2가 되고,
     * 다음 스냅샷이 와도 교정되지 않는다. 원천이 한 번 틀어지면 그걸로 만든 재계산도 전부 틀어지므로,
     * 판매와 같은 방식으로 eventId 장부를 둔다. 좋아요는 조회와 달리 양이 적어 장부 비용을 감당할 수 있다.
     */
    @Transactional
    public void applyLikeSnapshot(String eventId, Long productId, long likeCount, long likeDelta,
                                  ZonedDateTime occurredAt) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return;
        }
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        if (metrics.applyLikeSnapshot(likeCount, occurredAt)) {
            productMetricsRepository.save(metrics);
        }
        bucketOf(productId, occurredAt, SOURCE_UNKNOWN).addLikeDelta(likeDelta);
        eventHandledRepository.save(new EventHandled(eventId));
    }

    /**
     * 조회 이벤트를 소비해 view_count를 1 증가시키고, 그 시간대의 <b>유입 경로별</b> 버킷에도 누적한다.
     * 조회 수는 인기 지표라 소량의 중복 집계를 허용한다 — 매 조회 eventId를 event_handled에 남기면
     * (조회량 ≫ 좋아요량) 그 테이블이 폭증하므로 멱등 처리를 생략한다(at-least-once 근사 집계).
     * <p>
     * product_metrics.view_count는 경로 구분 없는 총량이다. 경로를 가려 쓰는 것은 버킷의 몫이다.
     */
    @Transactional
    public void applyView(Long productId, ZonedDateTime occurredAt, String source) {
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        metrics.addView(1L);
        productMetricsRepository.save(metrics);
        bucketOf(productId, occurredAt, source == null ? SOURCE_UNKNOWN : source).addView(1L);
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
        bucketOf(productId, occurredAt, SOURCE_UNKNOWN).addOrderAmount(amount);
    }

    /**
     * 이벤트 발생 시각·유입 경로가 속한 버킷을 가져온다(없으면 만든다).
     * 반환한 엔티티는 영속 상태라 호출부의 변경이 dirty checking으로 반영된다.
     */
    private ProductMetricsHourly bucketOf(Long productId, ZonedDateTime occurredAt, String source) {
        ZonedDateTime bucketHour = occurredAt.withZoneSameInstant(SEOUL).truncatedTo(ChronoUnit.HOURS);
        return productMetricsHourlyRepository.findByProductIdAndBucketHourAndSource(productId, bucketHour, source)
                .orElseGet(() -> productMetricsHourlyRepository.save(
                        ProductMetricsHourly.init(productId, bucketHour, source)));
    }
}
