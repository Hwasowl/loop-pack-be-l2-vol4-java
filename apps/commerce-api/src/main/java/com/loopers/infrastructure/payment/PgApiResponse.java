package com.loopers.infrastructure.payment;

/** PG 공통 응답 래퍼 ({meta, data}). */
public record PgApiResponse<T>(Metadata meta, T data) {

    public record Metadata(String result, String errorCode, String message) {
    }
}
