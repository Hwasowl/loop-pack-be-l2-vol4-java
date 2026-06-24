package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 콜백 미수신·타임아웃으로 PENDING에 남은 결제를 PG에 직접 조회해 확정한다(복구).
 * PG가 응답하지 않으면(게이트웨이 fallback=empty) 이번 주기는 건너뛰고 다음 주기에 재시도한다.
 */
@RequiredArgsConstructor
@Component
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;

    public void reconcilePending() {
        for (PaymentModel payment : paymentRepository.findAllByStatus(PaymentStatus.PENDING)) {
            if (payment.getTransactionKey() == null) {
                continue;
            }
            paymentGateway.queryStatus(payment.getTransactionKey(), payment.getUserId())
                .ifPresent(status -> reflect(payment.getTransactionKey(), status));
        }
    }

    private void reflect(String transactionKey, String pgStatus) {
        if ("SUCCESS".equals(pgStatus)) {
            paymentService.confirm(transactionKey, true, null);
        } else if ("FAILED".equals(pgStatus)) {
            paymentService.confirm(transactionKey, false, "PG 조회 결과 실패");
        }
    }
}
