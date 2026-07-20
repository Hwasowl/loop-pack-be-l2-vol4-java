package com.loopers.domain.productmetrics;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface ProductMetricsHourlyRepository {

    Optional<ProductMetricsHourly> findByProductIdAndBucketHourAndSource(
            Long productId, ZonedDateTime bucketHour, String source);

    ProductMetricsHourly save(ProductMetricsHourly hourly);
}
