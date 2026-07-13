package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 랭킹 점수 적재(쓰기) 포트. 구현은 infrastructure(Redis ZSET).
 * 이벤트 발생 시각 기준으로 일별·시간별 두 키에 가중 델타를 더한다.
 */
public interface RankingRepository {

    void incrementScore(ZonedDateTime occurredAt, Long productId, double delta);

    /** 콜드 스타트 완화 — from 일별 키의 점수에 weight를 곱해 to 일별 키로 이월(ZUNIONSTORE)한다. */
    void carryOver(LocalDate from, LocalDate to, double weight);
}
