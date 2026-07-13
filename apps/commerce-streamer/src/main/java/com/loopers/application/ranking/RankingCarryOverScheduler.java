package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 콜드 스타트 완화 — 매일 23:50(Asia/Seoul)에 오늘 랭킹의 일부를 내일 키로 미리 이월(Score Carry-Over)한다.
 * 자정 직후 새 키가 텅 비어 보이는 문제를, 전날 인기 상품의 점수 일부로 채운다.
 * 테스트 프로파일에선 기동하지 않는다(@Profile("!test")).
 */
@Slf4j
@Component
@Profile("!test")
public class RankingCarryOverScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;
    private final double carryOverWeight;

    public RankingCarryOverScheduler(
            RankingRepository rankingRepository,
            @Value("${ranking.carry-over.weight:0.1}") double carryOverWeight) {
        this.rankingRepository = rankingRepository;
        this.carryOverWeight = carryOverWeight;
    }

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOverToTomorrow() {
        try {
            LocalDate today = LocalDate.now(SEOUL);
            rankingRepository.carryOver(today, today.plusDays(1), carryOverWeight);
        } catch (Exception e) {
            // 이월 실패는 콜드 스타트 완화가 안 될 뿐, 소비 흐름과 무관하므로 로그만 남기고 삼킨다.
            log.warn("랭킹 Score Carry-Over 실패", e);
        }
    }
}
