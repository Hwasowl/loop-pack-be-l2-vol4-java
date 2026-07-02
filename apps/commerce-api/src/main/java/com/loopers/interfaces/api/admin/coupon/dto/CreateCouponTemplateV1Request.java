package com.loopers.interfaces.api.admin.coupon.dto;

import com.loopers.domain.coupon.CouponType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CreateCouponTemplateV1Request(
    @NotBlank String name,
    @NotNull CouponType type,
    @Positive long value,
    @Min(0) Long minOrderAmount,
    @NotNull LocalDateTime expiredAt,
    /** 선착순 발급 수량 한도. null이면 무제한. */
    @Positive Integer totalQuantity
) {
}
