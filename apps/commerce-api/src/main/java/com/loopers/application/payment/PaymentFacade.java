package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 합성. PG 호출이 끼므로 Facade에 트랜잭션을 두지 않는다 — 각 Service 호출이 자기 트랜잭션이고,
 * PG 호출은 어떤 트랜잭션에도 들어가지 않는다.
 */
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PaymentInfo requestPayment(Long userId, Long orderId, CardType cardType, String cardNo) {
        OrderModel order = orderService.getById(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        long amount = order.getFinalAmount().value();

        paymentService.createPending(orderId, userId, cardType, Money.of(amount));

        GatewayResult result = paymentGateway.requestPayment(
            new GatewayCommand(orderId, userId, cardType, cardNo, amount));
        if (result.accepted()) {
            paymentService.assignTransactionKey(orderId, result.transactionKey());
        }

        return PaymentInfo.from(paymentService.getByOrderId(orderId));
    }

    /**
     * PG 콜백 수신. 콜백 본문은 위·변조될 수 있으므로 신뢰하지 않는다 —
     * 콜백은 "확인 트리거"로만 쓰고, 실제 상태는 PG에 재조회(queryStatus)해 확정한다.
     * PG가 응답하지 않으면 PENDING을 유지하고 복구 스케줄러가 사후 확정한다.
     */
    public void handleCallback(String transactionKey) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        paymentGateway.queryStatus(transactionKey, payment.getUserId())
            .ifPresent(status -> {
                if ("SUCCESS".equals(status)) {
                    paymentService.confirm(transactionKey, true, null);
                } else if ("FAILED".equals(status)) {
                    paymentService.confirm(transactionKey, false, "PG 조회 결과 실패");
                }
            });
    }

    public PaymentInfo getStatus(Long userId, Long orderId) {
        PaymentModel payment = paymentService.getByOrderId(orderId);
        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다.");
        }
        return PaymentInfo.from(payment);
    }
}
