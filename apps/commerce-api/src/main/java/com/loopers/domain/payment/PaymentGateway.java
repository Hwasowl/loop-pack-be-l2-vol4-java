package com.loopers.domain.payment;

import java.util.Optional;

/** 외부 결제 시스템 추상화. 특정 PG에 도메인을 묶지 않기 위한 경계. */
public interface PaymentGateway {

    GatewayResult requestPayment(GatewayCommand command);

    /** 거래키로 PG의 현재 상태를 조회한다(복구용). PG가 응답하지 않으면 empty. */
    Optional<String> queryStatus(String transactionKey, Long userId);
}
