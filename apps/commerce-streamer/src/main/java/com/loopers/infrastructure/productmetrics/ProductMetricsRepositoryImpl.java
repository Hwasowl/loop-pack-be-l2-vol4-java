package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetrics;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return jpaRepository.findById(productId);
    }

    @Override
    public ProductMetrics save(ProductMetrics productMetrics) {
        return jpaRepository.save(productMetrics);
    }
}
