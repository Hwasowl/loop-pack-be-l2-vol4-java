package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponTemplateRepository {
    CouponTemplate save(CouponTemplate template);
    Optional<CouponTemplate> findById(Long id);
}
