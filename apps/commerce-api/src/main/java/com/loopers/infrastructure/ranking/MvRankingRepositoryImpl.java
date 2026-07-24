package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRank;
import com.loopers.domain.ranking.MvRankingRepository;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MvRankingRepositoryImpl implements MvRankingRepository {

    private final MvProductRankWeeklyJpaRepository weeklyRepository;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;

    @Override
    public List<Long> topProductIds(RankingPeriod period, String periodKey, long offset, int size) {
        Pageable pageable = PageRequest.of((int) (offset / size), size);
        List<? extends MvProductRank> rows = period == RankingPeriod.WEEKLY
                ? weeklyRepository.findByPeriodKeyOrderByRankNoAsc(periodKey, pageable)
                : monthlyRepository.findByPeriodKeyOrderByRankNoAsc(periodKey, pageable);
        return rows.stream().map(MvProductRank::getProductId).toList();
    }

    @Override
    public long size(RankingPeriod period, String periodKey) {
        return period == RankingPeriod.WEEKLY
                ? weeklyRepository.countByPeriodKey(periodKey)
                : monthlyRepository.countByPeriodKey(periodKey);
    }
}
