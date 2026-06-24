package com.loopers.domain.payment;

/**
 * PG 접수 결과.
 * accepted=true: PG가 거래키를 발급(PENDING 접수됨).
 * accepted=false: 타임아웃·서킷 Open 등으로 결과 불명 — 결제는 PENDING 유지, 폴링/복구가 확정.
 */
public record GatewayResult(boolean accepted, String transactionKey) {

    public static GatewayResult accepted(String transactionKey) {
        return new GatewayResult(true, transactionKey);
    }

    public static GatewayResult pending() {
        return new GatewayResult(false, null);
    }
}
