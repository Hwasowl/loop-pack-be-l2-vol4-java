package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueOutcome;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final CouponIssueRequestSender couponIssueRequestSender;

    public CouponInfo issue(Long userId, Long templateId) {
        return CouponInfo.from(couponService.issue(userId, templateId));
    }

    /** 선착순 발급 요청을 접수해 처리 파이프로 넘긴다. 실제 발급은 컨슈머가 비동기로 수행한다. */
    public void requestIssue(Long userId, Long templateId) {
        couponIssueRequestSender.send(userId, templateId);
    }

    /** 컨슈머가 소비한 발급 요청을 처리한다(선착순 수량·1인1매 판정). */
    public CouponIssueOutcome issueFromRequest(Long userId, Long templateId) {
        return couponService.issueByRequest(userId, templateId);
    }

    public List<MyCouponInfo> getMyCoupons(Long userId) {
        List<IssuedCoupon> issued = couponService.getMyCoupons(userId);
        List<Long> templateIds = issued.stream().map(IssuedCoupon::getCouponTemplateId).distinct().toList();
        Map<Long, CouponTemplate> templates = couponService.getTemplatesByIds(templateIds);
        ZonedDateTime now = ZonedDateTime.now();
        return issued.stream()
            .filter(coupon -> templates.containsKey(coupon.getCouponTemplateId()))
            .map(coupon -> MyCouponInfo.from(coupon, templates.get(coupon.getCouponTemplateId()), now))
            .toList();
    }
}
