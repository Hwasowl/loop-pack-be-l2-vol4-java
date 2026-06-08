package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponAdminService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponTemplate create(String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(new CouponTemplate(name, type, discountValue, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public CouponTemplate getTemplate(Long id) {
        return couponTemplateRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplate> getTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable);
    }

    @Transactional
    public CouponTemplate update(Long id, String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplate template = getTemplate(id);
        template.update(name, type, discountValue, minOrderAmount, expiredAt);
        return template;
    }

    @Transactional
    public void delete(Long id) {
        CouponTemplate template = getTemplate(id);
        if (issuedCouponRepository.existsByCouponTemplateId(template.getId())) {
            throw new CoreException(ErrorType.CONFLICT, "[id = " + id + "] 발급된 쿠폰이 있어 템플릿을 삭제할 수 없습니다.");
        }
        couponTemplateRepository.deleteById(template.getId());
    }

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> getIssues(Long couponTemplateId, Pageable pageable) {
        return issuedCouponRepository.findByCouponTemplateId(couponTemplateId, pageable);
    }
}
