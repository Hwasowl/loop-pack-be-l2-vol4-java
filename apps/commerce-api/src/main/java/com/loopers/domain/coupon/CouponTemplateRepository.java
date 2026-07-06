package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponTemplateRepository {
    CouponTemplate save(CouponTemplate template);
    Optional<CouponTemplate> findById(Long id);
    List<CouponTemplate> findAllByIds(Collection<Long> ids);
    Page<CouponTemplate> findAll(Pageable pageable);
    void deleteById(Long id);

    /**
     * 남은 수량이 있을 때만 발급 수량을 원자적으로 1 증가시킨다(선착순 정합성의 바닥).
     * @return 증가에 성공(슬롯 확보)하면 1, 소진됐으면 0.
     */
    int increaseIssuedIfAvailable(Long id);
}
