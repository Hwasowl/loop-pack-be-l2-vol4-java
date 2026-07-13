package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/**
 * 이벤트별 가중 델타를 계산해 랭킹 ZSET에 반영한다.
 * 가중치는 설정값으로 주입해 재배포 없이 조정할 여지를 둔다(총합 1이 되도록: 조회 0.1 / 좋아요 0.2 / 주문 0.6).
 * 멱등·순서 판단은 상류(ProductMetricsService)가 소유하고, 이 서비스는 "실제 반영된 델타"만 받아 더한다.
 */
@Service
public class RankingService {

    private final RankingRepository rankingRepository;
    private final double weightView;
    private final double weightLike;
    private final double weightOrder;

    public RankingService(
            RankingRepository rankingRepository,
            @Value("${ranking.weight.view:0.1}") double weightView,
            @Value("${ranking.weight.like:0.2}") double weightLike,
            @Value("${ranking.weight.order:0.6}") double weightOrder) {
        this.rankingRepository = rankingRepository;
        this.weightView = weightView;
        this.weightLike = weightLike;
        this.weightOrder = weightOrder;
    }

    /** 조회 1건 = +1 × 가중치. */
    public void applyView(ZonedDateTime occurredAt, Long productId) {
        rankingRepository.incrementScore(occurredAt, productId, weightView);
    }

    /** 좋아요 순증감(델타) × 가중치. 0이면(스냅샷 미반영) 건너뛰고, 언라이크면 음수 델타로 점수가 내려간다. */
    public void applyLikeDelta(ZonedDateTime occurredAt, Long productId, long likeDelta) {
        if (likeDelta == 0L) {
            return;
        }
        rankingRepository.incrementScore(occurredAt, productId, weightLike * likeDelta);
    }

    /** 주문 금액(단가×수량) × 가중치. 멱등 중복이면 amount=0으로 들어와 건너뛴다. */
    public void applyOrder(ZonedDateTime occurredAt, Long productId, long amount) {
        if (amount <= 0L) {
            return;
        }
        rankingRepository.incrementScore(occurredAt, productId, weightOrder * amount);
    }
}
