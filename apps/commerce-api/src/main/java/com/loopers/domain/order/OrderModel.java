package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "failure_reason")
    private String failureReason;

    protected OrderModel() {}

    public OrderModel(Long userId, Long totalAmount) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 비어있을 수 없습니다.");
        }
        if (totalAmount == null || totalAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "totalAmount는 0 이상이어야 합니다.");
        }
        this.userId = userId;
        this.status = OrderStatus.CREATED;
        this.totalAmount = totalAmount;
    }

    public void markSucceeded() {
        ensureCreated("SUCCEEDED");
        this.status = OrderStatus.SUCCEEDED;
    }

    public void markFailed(String reason) {
        ensureCreated("FAILED");
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
    }

    private void ensureCreated(String target) {
        if (this.status != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.CONFLICT,
                "[status = " + this.status + "] CREATED 상태에서만 " + target + "로 전이할 수 있습니다.");
        }
    }
}
