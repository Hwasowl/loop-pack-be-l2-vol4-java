package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeysTest {

    /**
     * 이 모듈이 존재하는 이유 — 쓰기(streamer)가 만든 키와 읽기(api)가 만든 키가 같아야 한다.
     * 어긋나면 점수는 쌓이는데 조회는 빈 랭킹을 내놓는, 아무도 에러를 못 보는 실패가 된다.
     */
    @DisplayName("쓰기 키와 읽기 키를 맞춰볼 때")
    @Nested
    class WriteReadContract {

        @DisplayName("이벤트 시각으로 적재한 일별 키는 그 날짜로 조회한 키와 같다")
        @Test
        void dailyWriteKey_matchesReadKey() {
            // given
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-13T15:30:00+09:00");

            // when
            String writeKey = RankingKeys.daily(occurredAt);
            String readKey = RankingKeys.of(LocalDate.of(2026, 1, 13), null);

            // then
            assertThat(writeKey).isEqualTo(readKey);
        }

        @DisplayName("이벤트 시각으로 적재한 시간별 키는 그 날짜·시로 조회한 키와 같다")
        @Test
        void hourlyWriteKey_matchesReadKey() {
            // given
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-13T15:30:00+09:00");

            // when
            String writeKey = RankingKeys.hourly(occurredAt);
            String readKey = RankingKeys.of(LocalDate.of(2026, 1, 13), 15);

            // then
            assertThat(writeKey).isEqualTo(readKey);
        }

        @DisplayName("UTC로 들어온 이벤트도 Asia/Seoul로 환산된 날짜로 조회하면 같은 키가 나온다")
        @Test
        void writeKeyAcrossMidnight_matchesSeoulDateReadKey() {
            // given - UTC 1/12 20:00 = Seoul 1/13 05:00
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-12T20:00:00Z");

            // when
            String writeKey = RankingKeys.daily(occurredAt);
            String readKey = RankingKeys.of(LocalDate.of(2026, 1, 13), null);

            // then
            assertThat(writeKey).isEqualTo(readKey);
        }

        @DisplayName("적재한 멤버를 조회 측에서 되읽으면 원래 상품 ID가 나온다")
        @Test
        void memberRoundTrips() {
            // when
            String member = RankingKeys.member(100L);

            // then
            assertThat(RankingKeys.productIdOf(member)).isEqualTo(100L);
        }
    }

    @DisplayName("조회용 키를 만들 때")
    @Nested
    class ReadKeys {

        @DisplayName("hour가 없으면 일별 키(yyyyMMdd)를 만든다")
        @Test
        void of_withoutHour_isDaily() {
            assertThat(RankingKeys.of(LocalDate.of(2026, 1, 13), null)).isEqualTo("ranking:all:20260113");
        }

        @DisplayName("hour가 한 자리여도 두 자리로 채워 시간별 키(yyyyMMddHH)를 만든다")
        @Test
        void of_withSingleDigitHour_isZeroPadded() {
            assertThat(RankingKeys.of(LocalDate.of(2026, 1, 13), 5)).isEqualTo("ranking:all:2026011305");
        }
    }

    @DisplayName("키를 만들 때")
    @Nested
    class Keys {

        @DisplayName("일별 키는 이벤트 시각의 Asia/Seoul 날짜로 yyyyMMdd 8자리 접미사를 만든다")
        @Test
        void daily_usesSeoulDate() {
            // given
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-13T15:30:00+09:00");

            // when
            String key = RankingKeys.daily(occurredAt);

            // then
            assertThat(key).isEqualTo("ranking:all:20260113");
        }

        @DisplayName("시간별 키는 이벤트 시각의 Asia/Seoul 날짜+시로 yyyyMMddHH 10자리 접미사를 만든다")
        @Test
        void hourly_usesSeoulDateHour() {
            // given
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-13T15:30:00+09:00");

            // when
            String key = RankingKeys.hourly(occurredAt);

            // then
            assertThat(key).isEqualTo("ranking:all:2026011315");
        }

        @DisplayName("UTC로 들어온 이벤트도 Asia/Seoul로 환산해 버킷팅한다(자정 경계에서 날짜가 넘어간다)")
        @Test
        void bucketsBySeoulZone_acrossMidnight() {
            // given - UTC 23:30 = KST 익일 08:30
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-13T23:30:00Z");

            // when & then - 날짜는 KST 기준으로 14일, 시는 08시
            assertThat(RankingKeys.daily(occurredAt)).isEqualTo("ranking:all:20260114");
            assertThat(RankingKeys.hourly(occurredAt)).isEqualTo("ranking:all:2026011408");
        }

        @DisplayName("멤버는 product:{productId} 형식이다")
        @Test
        void member_hasProductPrefix() {
            assertThat(RankingKeys.member(100L)).isEqualTo("product:100");
        }
    }

    @DisplayName("만료 시각을 계산할 때")
    @Nested
    class Expiry {

        @DisplayName("일별 키는 해당 날짜 자정 기준 +2일(Asia/Seoul)에 만료된다")
        @Test
        void dailyExpiry_isMidnightPlusTwoDays() {
            // given - 2026-01-13 KST 이벤트
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-13T15:30:00+09:00");

            // when
            Instant expiry = RankingKeys.dailyExpiry(occurredAt);

            // then - 2026-01-15T00:00 KST = 2026-01-14T15:00Z
            assertThat(expiry).isEqualTo(Instant.parse("2026-01-14T15:00:00Z"));
        }

        @DisplayName("시간별 키는 해당 정시 기준 +3시간(Asia/Seoul)에 만료된다")
        @Test
        void hourlyExpiry_isTopOfHourPlusThreeHours() {
            // given - 2026-01-13 15:30 KST 이벤트
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-01-13T15:30:00+09:00");

            // when
            Instant expiry = RankingKeys.hourlyExpiry(occurredAt);

            // then - 15:00 + 3h = 18:00 KST = 09:00Z
            assertThat(expiry).isEqualTo(Instant.parse("2026-01-13T09:00:00Z"));
        }
    }
}
