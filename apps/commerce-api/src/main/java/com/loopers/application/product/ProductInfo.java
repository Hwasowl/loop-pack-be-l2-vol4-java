package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record ProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    Long brandId,
    String brandName,
    Long likeCount,
    boolean available,
    Long rank
) {
    public static ProductInfo from(ProductModel product, BrandModel brand, boolean available, long likeCount) {
        // rank는 실시간 값이라 캐시 대상(목록/상세 본문)에서는 비운다 — 조회 시점에 withRank로 덧붙인다.
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice().value(),
            product.getBrandId(),
            brand.getName(),
            likeCount,
            available,
            null
        );
    }

    /** 당일 랭킹 순위(랭킹에 없으면 null)를 덧붙인 사본. */
    public ProductInfo withRank(Long rank) {
        return new ProductInfo(id, name, description, price, brandId, brandName, likeCount, available, rank);
    }
}
