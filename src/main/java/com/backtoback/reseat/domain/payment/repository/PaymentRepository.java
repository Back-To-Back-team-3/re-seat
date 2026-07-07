package com.backtoback.reseat.domain.payment.repository;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    Optional<Payment> findFirstByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
