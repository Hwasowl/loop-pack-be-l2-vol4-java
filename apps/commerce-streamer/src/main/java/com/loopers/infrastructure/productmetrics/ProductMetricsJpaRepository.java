package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {
}
