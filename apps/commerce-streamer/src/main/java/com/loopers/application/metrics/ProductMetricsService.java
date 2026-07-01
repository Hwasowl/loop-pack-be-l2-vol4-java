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
}
