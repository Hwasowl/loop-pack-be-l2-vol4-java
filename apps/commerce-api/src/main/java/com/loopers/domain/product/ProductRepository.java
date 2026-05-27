package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    List<ProductModel> findAllByIds(Collection<Long> ids);
    Page<ProductModel> search(Long brandId, SortOption sort, Pageable pageable);
    long countByBrandId(Long brandId);
    Map<Long, Long> countByBrandIds(Collection<Long> brandIds);

    /** 비정규화 like_count 원자 증감 — read-modify-write 사이 lost update를 막기 위해 단일 UPDATE로 처리. 영향 행 수를 반환한다. */
    int incrementLikeCount(Long id);
    int decrementLikeCount(Long id);
}
