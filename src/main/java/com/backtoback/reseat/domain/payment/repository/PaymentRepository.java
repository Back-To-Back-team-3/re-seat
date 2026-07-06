package com.backtoback.reseat.domain.payment.repository;

import com.backtoback.reseat.domain.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentNo(String paymentNo);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByPgPaymentKey(String pgPaymentKey);
}
