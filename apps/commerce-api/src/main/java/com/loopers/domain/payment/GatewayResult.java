package com.loopers.domain.payment;

/** accepted=false는 실패가 아니라 결과 불명(타임아웃·서킷 Open) — PENDING 유지, 폴링/복구가 확정한다. */
public record GatewayResult(boolean accepted, String transactionKey) {

    public static GatewayResult accepted(String transactionKey) {
        return new GatewayResult(true, transactionKey);
    }

    public static GatewayResult pending() {
        return new GatewayResult(false, null);
    }
}
