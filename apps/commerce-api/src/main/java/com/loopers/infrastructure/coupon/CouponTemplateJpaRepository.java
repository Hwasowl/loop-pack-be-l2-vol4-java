package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplate, Long> {

    /** 남은 수량이 있을 때만 issued_quantity를 원자적으로 +1 한다. 영향 행 수로 성공/소진을 구분한다. */
    @Modifying(clearAutomatically = true)
    @Query("update CouponTemplate t set t.issuedQuantity = t.issuedQuantity + 1 "
            + "where t.id = :id and t.deletedAt is null "
            + "and t.totalQuantity is not null and t.issuedQuantity < t.totalQuantity")
    int increaseIssuedIfAvailable(@Param("id") Long id);
}
