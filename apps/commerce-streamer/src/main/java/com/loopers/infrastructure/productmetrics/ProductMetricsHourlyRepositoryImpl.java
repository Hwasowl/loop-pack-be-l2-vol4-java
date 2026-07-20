package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsHourly;
import com.loopers.domain.productmetrics.ProductMetricsHourlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductMetricsHourlyRepositoryImpl implements ProductMetricsHourlyRepository {

    private final ProductMetricsHourlyJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetricsHourly> findByProductIdAndBucketHourAndSource(
            Long productId, ZonedDateTime bucketHour, String source) {
        return jpaRepository.findByProductIdAndBucketHourAndSource(productId, bucketHour, source);
    }

    @Override
    public ProductMetricsHourly save(ProductMetricsHourly hourly) {
        return jpaRepository.save(hourly);
    }
}
