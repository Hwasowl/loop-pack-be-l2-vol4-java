package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findByOrderId(Long orderId);

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    List<PaymentModel> findAllByStatus(PaymentStatus status);
}
