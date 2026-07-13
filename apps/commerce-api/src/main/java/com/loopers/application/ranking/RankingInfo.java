package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;

public record RankingInfo(
    long rank,
    Long productId,
    String name,
    Long price,
    Long brandId,
    Long likeCount
) {
    public static RankingInfo of(long rank, ProductModel product, long likeCount) {
        return new RankingInfo(
            rank,
            product.getId(),
            product.getName(),
            product.getPrice().value(),
            product.getBrandId(),
            likeCount
        );
    }
}
