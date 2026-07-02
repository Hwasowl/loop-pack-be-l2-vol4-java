package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetrics;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    /**
     * 좋아요 총량 스냅샷을 소비해 product_metrics.like_count에 최신-우선으로 반영한다.
     * 절대값 덮어쓰기라 중복·유실엔 강하고(다음 스냅샷이 교정), 순서 역전만 occurredAt 비교로 막는다.
     * 그래서 멱등 장부(event_handled)가 필요 없다 — 오래된 이벤트는 모델이 스스로 버린다.
     */
    @Transactional
    public void applyLikeSnapshot(Long productId, long likeCount, ZonedDateTime occurredAt) {
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        if (metrics.applyLikeSnapshot(likeCount, occurredAt)) {
            productMetricsRepository.save(metrics);
        }
    }

    /**
     * 조회 이벤트를 소비해 view_count를 1 증가시킨다.
     * 조회 수는 인기 지표라 소량의 중복 집계를 허용한다 — 매 조회 eventId를 event_handled에 남기면
     * (조회량 ≫ 좋아요량) 그 테이블이 폭증하므로 멱등 처리를 생략한다(at-least-once 근사 집계).
     */
    @Transactional
    public void applyView(Long productId) {
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        metrics.addView(1L);
        productMetricsRepository.save(metrics);
    }

    /**
     * 판매 이벤트를 소비해 product_metrics.sales_count에 수량을 더한다.
     * 판매량은 조회와 달리 이중집계에 민감하므로 eventId(주문 라인 기반 결정적 키)로 멱등 처리한다.
     */
    @Transactional
    public void applySold(String eventId, Long productId, int quantity) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return;
        }
        ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> ProductMetrics.init(productId));
        metrics.addSales(quantity);
        productMetricsRepository.save(metrics);
        eventHandledRepository.save(new EventHandled(eventId));
    }
}
