package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentRecoveryService recoveryService;

    private PaymentModel pending(String transactionKey) {
        PaymentModel payment = new PaymentModel(1L, 10L, CardType.SAMSUNG, Money.of(5_000L));
        if (transactionKey != null) {
            payment.assignTransactionKey(transactionKey);
        }
        return payment;
    }

    @DisplayName("PENDING 결제 복구 시")
    @Nested
    class Reconcile {

        @DisplayName("PG 조회 결과가 SUCCESS이면 해당 결제를 성공으로 확정한다")
        @Test
        void confirmsSuccess() {
            when(paymentRepository.findAllByStatus(PaymentStatus.PENDING)).thenReturn(List.of(pending("tx-1")));
            when(paymentGateway.queryStatus("tx-1", 10L)).thenReturn(Optional.of("SUCCESS"));

            recoveryService.reconcilePending();

            verify(paymentService).confirm("tx-1", true, null);
        }

        @DisplayName("PG 조회 결과가 FAILED이면 해당 결제를 실패로 확정한다")
        @Test
        void confirmsFailed() {
            when(paymentRepository.findAllByStatus(PaymentStatus.PENDING)).thenReturn(List.of(pending("tx-1")));
            when(paymentGateway.queryStatus("tx-1", 10L)).thenReturn(Optional.of("FAILED"));

            recoveryService.reconcilePending();

            verify(paymentService).confirm(eq("tx-1"), eq(false), any());
        }

        @DisplayName("PG가 응답하지 않으면(empty) 확정하지 않고 다음 주기로 미룬다")
        @Test
        void skipsWhenGatewayUnavailable() {
            when(paymentRepository.findAllByStatus(PaymentStatus.PENDING)).thenReturn(List.of(pending("tx-1")));
            when(paymentGateway.queryStatus("tx-1", 10L)).thenReturn(Optional.empty());

            recoveryService.reconcilePending();

            verify(paymentService, never()).confirm(any(), anyBoolean(), any());
        }

        @DisplayName("거래키가 없는 결제는 PG 조회도 확정도 하지 않는다")
        @Test
        void skipsWhenNoTransactionKey() {
            when(paymentRepository.findAllByStatus(PaymentStatus.PENDING)).thenReturn(List.of(pending(null)));

            recoveryService.reconcilePending();

            verify(paymentGateway, never()).queryStatus(any(), any());
            verify(paymentService, never()).confirm(any(), anyBoolean(), any());
        }
    }
}
