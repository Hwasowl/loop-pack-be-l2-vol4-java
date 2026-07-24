package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 주간 TOP 100 랭킹 MV (읽기 전용). */
@Entity
@Table(name = "mv_product_rank_weekly")
public class MvProductRankWeekly extends MvProductRank {
}
