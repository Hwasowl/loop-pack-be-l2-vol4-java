package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 ZSET 키/멤버 규약(읽기 측). commerce-streamer(쓰기)와 반드시 동일해야 하는 <b>교차 앱 계약</b>이다.
 * 조회는 date(+hour)로 버킷 키를 만든다 — hour가 없으면 일별, 있으면 시간별.
 */
public final class RankingKeys {

    public static final String PREFIX = "ranking:all:";
    public static final String MEMBER_PREFIX = "product:";

    private static final DateTimeFormatter DAILY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankingKeys() {
    }

    /** hour == null 이면 일별 키(yyyyMMdd), 있으면 시간별 키(yyyyMMddHH). */
    public static String of(LocalDate date, Integer hour) {
        String base = PREFIX + date.format(DAILY);
        return hour == null ? base : base + String.format("%02d", hour);
    }

    public static String member(Long productId) {
        return MEMBER_PREFIX + productId;
    }

    public static Long productIdOf(String member) {
        return Long.valueOf(member.substring(MEMBER_PREFIX.length()));
    }
}
