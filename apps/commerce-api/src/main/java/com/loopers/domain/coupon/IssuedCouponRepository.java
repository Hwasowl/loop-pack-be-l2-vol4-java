package com.loopers.domain.coupon;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon coupon);
    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);
}
