package com.loopers.domain.coupon;

import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon coupon);
    Optional<IssuedCoupon> findById(Long id);
}
