package com.backtoback.reseat.domain.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;

import jakarta.persistence.LockModeType;

public interface PaymentRecoveryTaskRepository extends JpaRepository<PaymentRecoveryTask, Long> {

	Optional<PaymentRecoveryTask> findByPayment_Id(Long paymentId);

	@Query("""
	    select task.id
	    from PaymentRecoveryTask task
	    where task.status = :pending
	    or (task.status = :retry and task.nextRetryAt <= :now)
	    order by task.createdAt asc
	    """)
	List<Long> findRecoverableTaskIds(
	    @Param("pending") PaymentRecoveryStatus pending,
	    @Param("retry") PaymentRecoveryStatus retry,
	    @Param("now") LocalDateTime now,
	    Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
	    select task
	    from PaymentRecoveryTask task
	    join fetch task.payment
	    where task.id = :taskId
	    """)
	Optional<PaymentRecoveryTask> findByIdWithPessimisticWriteLock(@Param("taskId") Long taskId);
}
