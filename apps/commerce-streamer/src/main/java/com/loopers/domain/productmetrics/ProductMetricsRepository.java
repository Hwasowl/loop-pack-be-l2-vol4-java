package com.loopers.domain.productmetrics;

import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetrics> findByProductId(Long productId);

    ProductMetrics save(ProductMetrics productMetrics);
}
