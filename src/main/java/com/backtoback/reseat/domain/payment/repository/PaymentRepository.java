package com.backtoback.reseat.domain.payment.repository;

import com.backtoback.reseat.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    Optional<Payment> findByOrder_Id(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdWithPessimisticWriteLock(@Param("paymentId") Long paymentId);
}
