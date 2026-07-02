package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public IssuedCoupon issue(Long userId, Long couponTemplateId) {
        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponTemplateId + "] 쿠폰을 찾을 수 없습니다."));
        if (template.isExpired(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return issuedCouponRepository.save(new IssuedCoupon(userId, couponTemplateId));
    }

    /**
     * 선착순 발급 요청을 처리한다(Kafka 컨슈머가 호출). 비동기라 예외 대신 결과 값을 반환한다.
     * 정합성: 1인 1매(존재검사) + 수량 한도(조건부 원자 UPDATE). 같은 트랜잭션에서 수량 증가와
     * 발급 저장을 묶어, 저장 실패 시 수량 증가도 함께 롤백된다.
     * 재전달돼도 존재검사가 DUPLICATE로 잡아 멱등하다(별도 인박스 불필요).
     */
    @Transactional
    public CouponIssueOutcome issueByRequest(Long userId, Long couponTemplateId) {
        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId).orElse(null);
        if (template == null) {
            return CouponIssueOutcome.NOT_FOUND;
        }
        if (template.isExpired(ZonedDateTime.now())) {
            return CouponIssueOutcome.EXPIRED;
        }
        if (issuedCouponRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)) {
            return CouponIssueOutcome.DUPLICATE;
        }
        if (template.hasQuantityLimit() && couponTemplateRepository.increaseIssuedIfAvailable(couponTemplateId) == 0) {
            return CouponIssueOutcome.SOLD_OUT;
        }
        issuedCouponRepository.save(new IssuedCoupon(userId, couponTemplateId));
        return CouponIssueOutcome.ISSUED;
    }

    @Transactional(readOnly = true)
    public List<IssuedCoupon> getMyCoupons(Long userId) {
        return issuedCouponRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Map<Long, CouponTemplate> getTemplatesByIds(Collection<Long> ids) {
        return couponTemplateRepository.findAllByIds(ids).stream()
            .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
    }

    @Transactional
    public Money use(Long userId, Long couponId, Money orderAmount) {
        IssuedCoupon coupon = issuedCouponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
        if (!coupon.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다.");
        }
        CouponTemplate template = couponTemplateRepository.findById(coupon.getCouponTemplateId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 정보를 찾을 수 없습니다."));
        if (template.isExpired(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
        if (!template.satisfiesMinOrderAmount(orderAmount)) {
            throw new CoreException(ErrorType.CONFLICT, "최소 주문 금액 조건을 충족하지 않습니다.");
        }
        coupon.use();
        return template.calculateDiscount(orderAmount);
    }

    @Transactional
    public void cancel(Long couponId) {
        IssuedCoupon coupon = issuedCouponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
        coupon.cancel();
    }
}
