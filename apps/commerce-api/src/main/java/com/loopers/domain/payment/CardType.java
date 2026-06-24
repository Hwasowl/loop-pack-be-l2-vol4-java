package com.loopers.domain.payment;

/**
 * 우리 도메인이 소유하는 카드사 타입.
 * 외부 PG가 요구하는 표현으로의 변환은 PG 클라이언트 경계에서 매핑한다 (특정 PG에 도메인을 묶지 않기 위함).
 */
public enum CardType {
    SAMSUNG,
    KB,
    HYUNDAI
}
