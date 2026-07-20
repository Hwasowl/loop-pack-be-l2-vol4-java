package com.loopers.interfaces.api.ranking.dto;

import com.loopers.application.ranking.RankingInfo;

public record RankingV1Response(
    long rank,
    Long productId,
    String name,
    Long price,
    Long brandId,
    Long likeCount
) {
    public static RankingV1Response from(RankingInfo info) {
        return new RankingV1Response(
            info.rank(),
            info.productId(),
            info.name(),
            info.price(),
            info.brandId(),
            info.likeCount()
        );
    }
}
