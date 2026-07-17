package com.loopers.domain.productmetrics;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface ProductMetricsHourlyRepository {

    Optional<ProductMetricsHourly> findByProductIdAndBucketHour(Long productId, ZonedDateTime bucketHour);

    ProductMetricsHourly save(ProductMetricsHourly hourly);
}
