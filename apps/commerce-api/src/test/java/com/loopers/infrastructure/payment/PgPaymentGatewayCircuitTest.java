package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.PaymentGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PG 게이트웨이의 서킷 브레이커 동작 검증.
 * 실패율이 임계치(50%)를 "실제로" 넘을 때만 OPEN되는지, OPEN이면 PG 호출을 끊는지(fast-fail)를 본다.
 * 윈도우/임계치를 테스트용으로 작게 덮고, 재시도는 꺼서(호출:집계 1:1) 임계치 경계를 또렷이 본다.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "resilience4j.circuitbreaker.instances.pgCircuit.sliding-window-type=COUNT_BASED",
    "resilience4j.circuitbreaker.instances.pgCircuit.sliding-window-size=10",
    "resilience4j.circuitbreaker.instances.pgCircuit.minimum-number-of-calls=10",
    "resilience4j.circuitbreaker.instances.pgCircuit.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.pgCircuit.wait-duration-in-open-state=60s",
    "resilience4j.circuitbreaker.instances.pgCircuit.permitted-number-of-calls-in-half-open-state=3",
    "resilience4j.retry.instances.pgRetry.max-attempts=1"
})
class PgPaymentGatewayCircuitTest {

    @MockitoBean
    private PgClient pgClient;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final GatewayCommand CMD =
        new GatewayCommand(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 5_000L);

    private CircuitBreaker circuit() {
        return circuitBreakerRegistry.circuitBreaker("pgCircuit");
    }

    @BeforeEach
    void resetCircuit() {
        circuit().reset();
    }

    /** 앞에서부터 failCount건은 실패(예외), 이후는 성공(거래키 반환)하도록 스텁한다. */
    private void stubFailFirst(int failCount) {
        AtomicInteger calls = new AtomicInteger();
        when(pgClient.requestPayment(any())).thenAnswer(inv -> {
            if (calls.incrementAndGet() <= failCount) {
                throw new RuntimeException("PG 5xx 모의 실패");
            }
            return "tx-" + calls.get();
        });
    }

    @DisplayName("실패율 기준으로")
    @Nested
    class FailureRate {

        @DisplayName("윈도우 10건 중 6건(60%)이 실패해 임계치(50%)를 넘으면 서킷이 OPEN된다")
        @Test
        void opensWhenFailureRateExceedsThreshold() {
            stubFailFirst(6);

            for (int i = 0; i < 10; i++) {
                paymentGateway.requestPayment(CMD);
            }

            assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @DisplayName("윈도우 10건 중 4건(40%)만 실패해 임계치(50%) 미만이면 서킷은 CLOSED를 유지한다")
        @Test
        void staysClosedWhenBelowThreshold() {
            stubFailFirst(4);

            for (int i = 0; i < 10; i++) {
                paymentGateway.requestPayment(CMD);
            }

            assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @DisplayName("서킷이 OPEN이면")
    @Nested
    class WhenOpen {

        @DisplayName("PG를 더 호출하지 않고(fast-fail) PENDING fallback을 반환한다")
        @Test
        void shortCircuitsToFallback() {
            stubFailFirst(10);
            for (int i = 0; i < 10; i++) {
                paymentGateway.requestPayment(CMD);
            }
            assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.OPEN);

            GatewayResult result = paymentGateway.requestPayment(CMD);

            assertThat(result.accepted()).isFalse();
            verify(pgClient, times(10)).requestPayment(any());
        }
    }
}
