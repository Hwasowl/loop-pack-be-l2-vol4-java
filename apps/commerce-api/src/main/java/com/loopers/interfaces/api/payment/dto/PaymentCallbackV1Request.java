package com.loopers.interfaces.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCallbackV1Request(
    String transactionKey,
    String status,
    String reason
) {
}
