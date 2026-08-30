package com.backtoback.reseat.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.payment.entity.PaymentCancel;

import jakarta.persistence.LockModeType;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    /** 동일 티켓의 결제 취소 이력을 비관적 쓰기 락으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select paymentCancel from PaymentCancel paymentCancel where paymentCancel.ticket.id = :ticketId")
    Optional<PaymentCancel> findByTicketIdWithPessimisticWriteLock(@Param("ticketId") Long ticketId);
}
