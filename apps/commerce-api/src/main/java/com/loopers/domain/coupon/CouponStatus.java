package com.loopers.domain.coupon;

public enum CouponStatus {
    /** 사용 가능. 저장되는 상태. */
    AVAILABLE,
    /** 사용 완료. 저장되는 상태. 재사용 불가. */
    USED,
    /** 만료. 저장하지 않고 조회 시점에 템플릿 만료일시와 비교해 파생 판정하는 표현용 상태. */
    EXPIRED
}
