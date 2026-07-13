package com.loopers.domain.ranking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 랭킹 ZSET 키/멤버 규약. commerce-api(읽기)와 반드시 동일해야 하는 <b>교차 앱 계약</b>이라
 * 두 앱에 같은 규약을 둔다(product_metrics 테이블/컬럼명을 양쪽이 공유하는 것과 같은 맥락).
 * 키는 이벤트 발생 시각(occurredAt)을 Asia/Seoul로 환산해 버킷팅한다 — 경계(자정/정시) 근처의
 * 지연·재처리 이벤트가 엉뚱한 버킷에 꽂히는 것을 막기 위함이다.
 */
public final class RankingKeys {

    public static final String PREFIX = "ranking:all:";
    public static final String MEMBER_PREFIX = "product:";

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAILY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOURLY = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final int DAILY_TTL_DAYS = 2;
    private static final int HOURLY_TTL_HOURS = 3;

    private RankingKeys() {
    }

    public static String daily(ZonedDateTime occurredAt) {
        return dailyKey(occurredAt.withZoneSameInstant(ZONE).toLocalDate());
    }

    /** Asia/Seoul 날짜로 바로 일별 키를 만든다(Carry-Over 스케줄러용). */
    public static String dailyKey(LocalDate date) {
        return PREFIX + date.format(DAILY);
    }

    public static String hourly(ZonedDateTime occurredAt) {
        return PREFIX + occurredAt.withZoneSameInstant(ZONE).format(HOURLY);
    }

    public static String member(Long productId) {
        return MEMBER_PREFIX + productId;
    }

    /** 일별 키 만료 시각 — 해당 날짜 자정 기준 +2일(최대 48시간). 자정 롤오버 후에도 전날 랭킹 조회 보장. */
    public static Instant dailyExpiry(ZonedDateTime occurredAt) {
        return dailyExpiry(occurredAt.withZoneSameInstant(ZONE).toLocalDate());
    }

    public static Instant dailyExpiry(LocalDate date) {
        return date.plusDays(DAILY_TTL_DAYS).atStartOfDay(ZONE).toInstant();
    }

    /** 시간별 키 만료 시각 — 해당 정시 기준 +3시간. 정시 직후 이전 시간대 조회 보장. */
    public static Instant hourlyExpiry(ZonedDateTime occurredAt) {
        return occurredAt.withZoneSameInstant(ZONE)
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(HOURLY_TTL_HOURS).toInstant();
    }
}
