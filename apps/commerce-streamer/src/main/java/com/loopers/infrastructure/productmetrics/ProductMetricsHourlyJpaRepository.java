package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsHourly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface ProductMetricsHourlyJpaRepository extends JpaRepository<ProductMetricsHourly, Long> {

    Optional<ProductMetricsHourly> findByProductIdAndBucketHour(Long productId, ZonedDateTime bucketHour);
}
