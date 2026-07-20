package com.loopers.domain.ranking;

import java.util.List;
import java.util.Optional;

/**
 * 랭킹 조회(읽기) 포트. 구현은 infrastructure(Redis ZSET).
 * 버킷 키는 RankingKeys가 만든다 — 이 포트는 이미 만들어진 키 문자열을 받는다.
 */
public interface RankingRepository {

    /** 점수 내림차순 Top-N productId 목록 (offset부터 size개). 순위 순서를 보존한다. */
    List<Long> topProductIds(String key, long offset, long size);

    /** 특정 상품의 순위(0-based). 랭킹에 없으면 empty. */
    Optional<Long> rank(String key, Long productId);

    /** 랭킹에 오른 전체 상품 수. */
    long size(String key);
}
