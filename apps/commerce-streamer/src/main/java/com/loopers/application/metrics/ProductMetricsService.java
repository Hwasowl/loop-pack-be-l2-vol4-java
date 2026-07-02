package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.productmetrics.ProductMetrics;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private static final String EVENT_LIKED = "PRODUCT_LIKED";
    private static final String EVENT_UNLIKED = "PRODUCT_UNLIKED";

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    /**
     * 좋아요/취소 이벤트를 소비해 product_metrics.like_count에 반영한다.
     * eventId로 멱등 처리하여 같은 이벤트가 중복 도착해도 한 번만 반영한다.
     */
    @Transactional
    public void applyLike(String eventId, String eventType, Long productId) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return;
        }
        long delta = switch (eventType) {
            case EVENT_LIKED -> 1L;
            case EVENT_UNLIKED -> -1L;
            default -> 0L;
        };
        if (delta != 0L) {
            ProductMetrics metrics = productMetricsRepository.findByProductId(productId)
                    .orElseGet(() -> ProductMetrics.init(productId));
            metrics.addLike(delta);
            productMetricsRepository.save(metrics);
        }
        eventHandledRepository.save(new EventHandled(eventId));
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
