package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.coupon.dto.IssueCouponV1Response;
import com.loopers.interfaces.api.coupon.dto.MyCouponV1Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급",
        description = "쿠폰 템플릿(couponId)으로 쿠폰을 발급받습니다. 같은 템플릿을 여러 번 발급받을 수 있습니다."
    )
    ApiResponse<IssueCouponV1Response> issue(AuthUser authUser, @Positive Long couponId);

    @Operation(
        summary = "선착순 쿠폰 발급 요청",
        description = "발급 요청을 접수(202)만 하고 실제 발급은 비동기로 순차 처리됩니다. 발급 여부는 '내 쿠폰 목록'에서 확인하세요."
    )
    ApiResponse<Object> requestIssue(AuthUser authUser, @Positive Long templateId);

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "발급받은 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 조회합니다."
    )
    ApiResponse<List<MyCouponV1Response>> getMyCoupons(AuthUser authUser);
}
