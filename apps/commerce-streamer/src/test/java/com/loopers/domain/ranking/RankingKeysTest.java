package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeysTest {

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
