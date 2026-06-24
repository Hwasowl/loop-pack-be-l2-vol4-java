package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.domain.common.MoneyConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(
    name = "payment",
    uniqueConstraints = {@UniqueConstraint(name = "uk_payment_order_id", columnNames = "order_id")}
)
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "amount", nullable = false)
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "reason")
    private String reason;

    protected PaymentModel() {}

    public PaymentModel(Long orderId, Long userId, CardType cardType, Money amount) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 비어있을 수 없습니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 비어있을 수 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 비어있을 수 없습니다.");
        }
        if (amount == null || amount.value() <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    /** PG가 발급한 거래키를 연결한다. */
    public void assignTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    /** 결제 성공 확정. 이미 SUCCESS면 멱등 — 중복 콜백·폴링에 안전. */
    public void markSuccess() {
        if (this.status == PaymentStatus.SUCCESS) {
            return;
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "확정할 수 없는 결제 상태입니다: " + this.status);
        }
        this.status = PaymentStatus.SUCCESS;
    }

    /** 결제 실패 확정. 이미 FAILED면 멱등. */
    public void markFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) {
            return;
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.CONFLICT, "확정할 수 없는 결제 상태입니다: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }
}
