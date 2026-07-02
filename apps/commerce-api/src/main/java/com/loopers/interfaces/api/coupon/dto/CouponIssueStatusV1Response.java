package com.loopers.interfaces.api.coupon.dto;

/**
 * 선착순 발급 요청의 번호표(requestId)와 상태.
 * status: PENDING(처리 전) / ISSUED / SOLD_OUT / DUPLICATE / EXPIRED / NOT_FOUND
 */
public record CouponIssueStatusV1Response(String requestId, String status) {
}
