package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.ZonedDateTime;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * coupon-issue-requests 배선 검증(임베디드 Kafka) — 발급요청 → 실제 브로커 → @KafkaListener → 발급.
 * 직렬화 계약(JsonSerializer↔ByteArrayDeserializer)과 리스너 배선은 Mock으로 못 잡으므로 여기서만 본다.
 * 선착순 수량·1인1매 같은 정합성은 H2 동시성 테스트에서 커버 — 여기선 해피패스 배선만.
 */
@SpringBootTest(properties = {
    "coupon.issue-consumer=kafka",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.properties.auto.offset.reset=earliest"
})
@EmbeddedKafka(topics = "coupon-issue-requests", partitions = 1)
class CouponIssueKafkaFlowTest {

    @Autowired
    private CouponFacade couponFacade;
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

    private void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("조건이 시간 내에 충족되지 않았습니다");
    }

    @DisplayName("발급 요청이 브로커를 거쳐 소비되면 선착순 쿠폰이 발급된다")
    @Test
    void issuesCoupon_whenRequestConsumed() {
        // given
        Long userId = 1L;
        CouponTemplate template = couponTemplateRepository.save(new CouponTemplate(
            "선착순 3장", CouponType.FIXED, 1_000L, null, ZonedDateTime.now().plusDays(7), 3));

        // when — 요청이 실제(임베디드) 브로커로 발행됨
        couponFacade.requestIssue(userId, template.getId());

        // then — 컨슈머가 비동기로 받아 발급할 때까지 대기
        awaitUntil(() -> !issuedCouponRepository.findAllByUserId(userId).isEmpty());
        assertAll(
            () -> assertThat(issuedCouponRepository.findAllByUserId(userId)).hasSize(1),
            () -> assertThat(couponTemplateRepository.findById(template.getId()).orElseThrow().getIssuedQuantity()).isEqualTo(1)
        );
    }
}
