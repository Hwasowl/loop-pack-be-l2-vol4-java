package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.coupon.dto.CouponIssueStatusV1Response;
import com.loopers.interfaces.api.coupon.dto.IssueCouponV1Response;
import com.loopers.interfaces.api.coupon.dto.MyCouponV1Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    @Override
    public ApiResponse<IssueCouponV1Response> issue(@LoginUser AuthUser authUser, @PathVariable Long couponId) {
        return ApiResponse.success(IssueCouponV1Response.from(couponFacade.issue(authUser.id(), couponId)));
    }

    @PostMapping("/coupons/{templateId}/issue-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public ApiResponse<CouponIssueStatusV1Response> requestIssue(@LoginUser AuthUser authUser, @PathVariable Long templateId) {
        String requestId = couponFacade.requestIssue(authUser.id(), templateId);
        return ApiResponse.success(new CouponIssueStatusV1Response(requestId, CouponFacade.PENDING_STATUS));
    }

    @GetMapping("/coupons/issue-requests/{requestId}")
    @Override
    public ApiResponse<CouponIssueStatusV1Response> getIssueStatus(@LoginUser AuthUser authUser, @PathVariable String requestId) {
        return ApiResponse.success(new CouponIssueStatusV1Response(requestId, couponFacade.getIssueStatus(requestId)));
    }

    @GetMapping("/users/me/coupons")
    @Override
    public ApiResponse<List<MyCouponV1Response>> getMyCoupons(@LoginUser AuthUser authUser) {
        List<MyCouponV1Response> coupons = couponFacade.getMyCoupons(authUser.id()).stream()
            .map(MyCouponV1Response::from)
            .toList();
        return ApiResponse.success(coupons);
    }
}
