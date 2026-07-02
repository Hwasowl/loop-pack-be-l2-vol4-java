package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 선착순 발급 요청의 처리 결과 장부(=Inbox 겸 결과 조회용).
 * requestId로 멱등을 보장하고, 유저는 이 결과를 polling으로 확인한다(발급/품절/중복).
 */
@Getter
@Entity
@Table(name = "coupon_issue_request")
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private CouponIssueOutcome outcome;

    protected CouponIssueRequest() {}

    public CouponIssueRequest(String requestId, Long userId, Long couponTemplateId, CouponIssueOutcome outcome) {
        if (requestId == null || requestId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 ID는 비어있을 수 없습니다.");
        }
        this.requestId = requestId;
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.outcome = outcome;
    }
}
