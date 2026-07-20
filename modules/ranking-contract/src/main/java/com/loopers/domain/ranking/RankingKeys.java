package com.loopers.domain.ranking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 랭킹 ZSET 키/멤버 규약. commerce-streamer(쓰기)와 commerce-api(읽기)가 <b>반드시 동일하게</b> 써야 하는
 * 교차 앱 계약이라 양쪽이 함께 의존하는 이 모듈이 소유한다. 두 앱에 같은 규약을 복사해 두면 한쪽만
 * 바뀌었을 때 컴파일러가 잡아주지 못하고, 키가 어긋나 조용히 빈 랭킹이 나간다.
 * <p>
 * 이 모듈은 키의 <i>모양</i>만 안다 — Redis 접속·명령은 modules:redis가, 어떤 이벤트에 몇 점을 줄지는
 * 각 앱의 정책이 각각 소유한다.
 * <p>
 * 쓰기 측은 이벤트 발생 시각(occurredAt)으로, 읽기 측은 조회 대상 날짜(date)로 키를 만든다. 쓰기에서
 * 지금 시각이 아닌 발생 시각을 쓰는 이유는, 경계(자정/정시) 근처의 지연·재처리 이벤트가 엉뚱한 버킷에
 * 꽂히는 것을 막기 위함이다.
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

    /**
     * 조회용 — hour == null 이면 일별 키(yyyyMMdd), 있으면 시간별 키(yyyyMMddHH).
     * 범위를 벗어난 hour는 여기서 막는다 — 쓰기 측은 실제 시각에서 버킷을 뽑아 24 같은 값이 나올 수 없는데,
     * 읽기 측은 요청이 준 값을 그대로 받는다. 걸러내지 않으면 존재할 수 없는 키를 조회해 빈 랭킹을 정상 응답으로 돌려준다.
     */
    public static String of(LocalDate date, Integer hour) {
        String base = dailyKey(date);
        if (hour == null) {
            return base;
        }
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour는 0~23이어야 합니다: " + hour);
        }
        return base + String.format("%02d", hour);
    }

    /** 적재용 — 이벤트 발생 시각을 Asia/Seoul로 환산해 일별 버킷을 정한다. */
    public static String daily(ZonedDateTime occurredAt) {
        return dailyKey(occurredAt.withZoneSameInstant(ZONE).toLocalDate());
    }

    /** Asia/Seoul 날짜로 바로 일별 키를 만든다(Carry-Over 스케줄러용). */
    public static String dailyKey(LocalDate date) {
        return PREFIX + date.format(DAILY);
    }

    /** 적재용 — 이벤트 발생 시각을 Asia/Seoul로 환산해 시간별 버킷을 정한다. */
    public static String hourly(ZonedDateTime occurredAt) {
        return PREFIX + occurredAt.withZoneSameInstant(ZONE).format(HOURLY);
    }

    public static String member(Long productId) {
        return MEMBER_PREFIX + productId;
    }

    public static Long productIdOf(String member) {
        return Long.valueOf(member.substring(MEMBER_PREFIX.length()));
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
