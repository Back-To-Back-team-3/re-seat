package com.backtoback.reseat.domain.payment.repository;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRecoveryTaskRepository extends JpaRepository<PaymentRecoveryTask, Long> {

    Optional<PaymentRecoveryTask> findByPayment_Id(Long paymentId);
}
