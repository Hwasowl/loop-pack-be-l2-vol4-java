package com.loopers.domain.coupon;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 선착순 발급(issueByRequest)의 정합성 검증.
 * 조건부 원자 UPDATE(issued &lt; total)로, 동시에 몰려도 한도만큼만 발급되고 오버셀이 없어야 한다.
 */
@SpringBootTest
class CouponFcfsIssueIntegrationTest {

    @Autowired
    private CouponService couponService;
    @Autowired
    private CouponTemplateRepository couponTemplateRepository;
    @Autowired
    private IssuedCouponRepository issuedCouponRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponTemplate limitedTemplate(int total) {
        return couponTemplateRepository.save(new CouponTemplate(
            "선착순 " + total + "장", CouponType.FIXED, 1_000L, null, ZonedDateTime.now().plusDays(7), total));
    }

    @DisplayName("한도 5장 선착순에 20명이 동시에 요청하면 정확히 5명만 발급되고 나머지는 SOLD_OUT이다")
    @Test
    void issuesExactlyLimit_whenRequestedConcurrently() throws InterruptedException {
        // given
        int total = 5;
        int threadCount = 20;
        Long templateId = limitedTemplate(total).getId();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger issued = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();

        // when - 유저마다(userId=1..20) 동시에 발급 요청
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    startGate.await();
                    CouponIssueOutcome outcome = couponService.issueByRequest("req-user-" + userId, userId, templateId);
                    if (outcome == CouponIssueOutcome.ISSUED) {
                        issued.incrementAndGet();
                    } else if (outcome == CouponIssueOutcome.SOLD_OUT) {
                        soldOut.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        // then - 정확히 한도만큼만 발급, 발급 수량도 한도에서 멈춤(오버셀 없음)
        assertAll(
            () -> assertThat(issued.get()).isEqualTo(total),
            () -> assertThat(soldOut.get()).isEqualTo(threadCount - total),
            () -> assertThat(couponTemplateRepository.findById(templateId).orElseThrow().getIssuedQuantity()).isEqualTo(total)
        );
    }

    @DisplayName("발급 요청 처리 시")
    @Nested
    class IssueByRequest {

        @DisplayName("같은 유저가 같은 템플릿을 두 번 요청하면 두 번째는 DUPLICATE로 스킵된다(1인 1매)")
        @Test
        void returnsDuplicate_whenSameUserRequestsTwice() {
            Long templateId = limitedTemplate(10).getId();

            CouponIssueOutcome first = couponService.issueByRequest("req-1", 1L, templateId);
            CouponIssueOutcome second = couponService.issueByRequest("req-2", 1L, templateId);

            assertAll(
                () -> assertThat(first).isEqualTo(CouponIssueOutcome.ISSUED),
                () -> assertThat(second).isEqualTo(CouponIssueOutcome.DUPLICATE),
                () -> assertThat(issuedCouponRepository.findAllByUserId(1L)).hasSize(1),
                () -> assertThat(couponTemplateRepository.findById(templateId).orElseThrow().getIssuedQuantity()).isEqualTo(1)
            );
        }

        @DisplayName("만료된 템플릿에 요청하면 EXPIRED로 발급되지 않는다")
        @Test
        void returnsExpired_whenTemplateExpired() {
            CouponTemplate expired = couponTemplateRepository.save(new CouponTemplate(
                "만료", CouponType.FIXED, 1_000L, null, ZonedDateTime.now().minusDays(1), 10));

            CouponIssueOutcome outcome = couponService.issueByRequest("req-exp", 1L, expired.getId());

            assertThat(outcome).isEqualTo(CouponIssueOutcome.EXPIRED);
            assertThat(issuedCouponRepository.findAllByUserId(1L)).isEmpty();
        }

        @DisplayName("같은 requestId가 재전달되면 기록된 결과를 그대로 반환하고 중복 발급하지 않는다(멱등)")
        @Test
        void isIdempotent_whenSameRequestIdRedelivered() {
            Long templateId = limitedTemplate(10).getId();

            CouponIssueOutcome first = couponService.issueByRequest("same-req", 1L, templateId);
            CouponIssueOutcome again = couponService.issueByRequest("same-req", 1L, templateId);

            assertAll(
                () -> assertThat(first).isEqualTo(CouponIssueOutcome.ISSUED),
                () -> assertThat(again).isEqualTo(CouponIssueOutcome.ISSUED),
                () -> assertThat(issuedCouponRepository.findAllByUserId(1L)).hasSize(1),
                () -> assertThat(couponTemplateRepository.findById(templateId).orElseThrow().getIssuedQuantity()).isEqualTo(1)
            );
        }
    }
}
