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
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    /** 발급 결과가 아직 확정되지 않은 상태값(요청 접수 후 처리 전). */
    public static final String PENDING_STATUS = "PENDING";

    private final CouponService couponService;
    private final CouponIssueRequestSender couponIssueRequestSender;

    public CouponInfo issue(Long userId, Long templateId) {
        return CouponInfo.from(couponService.issue(userId, templateId));
    }

    /**
     * 선착순 발급 요청을 접수해 처리 파이프로 넘긴다. 실제 발급은 컨슈머가 비동기로 수행한다.
     * 유저가 결과를 조회할 번호표(requestId)를 발급해 반환한다.
     */
    public String requestIssue(Long userId, Long templateId) {
        String requestId = UUID.randomUUID().toString();
        couponIssueRequestSender.send(requestId, userId, templateId);
        return requestId;
    }

    /** 컨슈머가 소비한 발급 요청을 처리한다(선착순 수량·1인1매 판정). */
    public CouponIssueOutcome issueFromRequest(String requestId, Long userId, Long templateId) {
        return couponService.issueByRequest(requestId, userId, templateId);
    }

    /** 발급 요청 결과를 조회한다(polling). 아직 처리 전이면 PENDING. */
    public String getIssueStatus(String requestId) {
        return couponService.getRequestOutcome(requestId)
            .map(Enum::name)
            .orElse(PENDING_STATUS);
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
