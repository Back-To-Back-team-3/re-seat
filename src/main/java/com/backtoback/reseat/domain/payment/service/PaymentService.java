package com.backtoback.reseat.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCompleteResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentFailResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentCancel;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelStatusUnknownException;
import com.backtoback.reseat.domain.payment.exception.PaymentLocalApplyFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentLockFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.repository.PaymentCancelRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.ticket.entity.Ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long PAYMENT_LOCK_WAIT_SECONDS = 3L;

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;
    private final PaymentCreationService paymentCreationService;
    private final PaymentApprovalService paymentApprovalService;
    private final PaymentServiceValidator paymentValidator;
    private final RedissonClient redissonClient;
    private final OrderService orderService;

    /**
     * 주문 기준 결제를 요청한다.
     * <p>Idempotency-Key가 이미 사용된 경우 기존 결제 요청을 검증해 같은 결과를 반환하고, 처음 사용된 키라면 새 결제를 생성한다.
     *
     * @param userId 현재 사용자 ID
     * @param idempotencyKey 중복 결제 방지 키
     * @param request 결제 요청 정보
     * @return 결제 처리 결과
     */
    public PaymentCreateResponse requestPayment(Long userId, String idempotencyKey, PaymentRequest request) {
        paymentValidator.validateIdempotencyKey(idempotencyKey);

        RLock lock = redissonClient.getLock(paymentCreationLockKey(request.getOrderId()));
        boolean locked = false;

        try {
            locked = lock.tryLock(PAYMENT_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new PaymentLockFailedException();
            }

            return paymentCreationService.requestPayment(userId, idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            log.warn("결제 생성 DB 충돌 후 기존 결제 재조회 (orderId={})", request.getOrderId());
            return paymentCreationService.requestPayment(userId, idempotencyKey, request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentLockFailedException();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 토스 위젯 인증 완료 후 전달받은 결제 정보로 토스 승인(confirm) API를 동기 호출해 결제를 확정한다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @param idempotencyKey 현재 결제 시도의 활성 멱등키
     * @param request 토스가 클라이언트에 돌려준 paymentKey/orderId/amount
     * @return 확정된 결제 결과
     */
    public PaymentCompleteResponse completePayment(
        Long userId,
        Long paymentId,
        String idempotencyKey,
        PaymentCompleteRequest request
    ) {
        try {
            return paymentApprovalService.approve(userId, paymentId, idempotencyKey, request);
        } catch (PaymentLocalApplyFailedException e) {
            // 승인 트랜잭션이 롤백된 뒤 별도 트랜잭션으로 PG 승인 취소 작업을 보존한다.
            boolean compensationRegistered
                = paymentApprovalService.registerApprovalCompensation(e.getPaymentId(), e.getPaymentKey());
            if (compensationRegistered) {
                failOrderAfterLocalApplyFailure(e.getOrderId());
            }
            throw e;
        }
    }

    /**
     * 위젯 취소 또는 실패 리다이렉트 시 결제를 실패로 기록한다. 토스 API는 호출하지 않는다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @param idempotencyKey 현재 결제 시도의 활성 멱등키
     * @param request 토스가 클라이언트에 돌려준 실패 code/message/orderId
     * @return 실패 처리된 결제 결과
     */
    @Transactional
    public PaymentFailResponse failPayment(
        Long userId,
        Long paymentId,
        String idempotencyKey,
        PaymentFailRequest request
    ) {
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        paymentValidator.validateActiveIdempotencyKey(payment, idempotencyKey);
        if (!payment.isReady()) {
            return PaymentFailResponse.from(payment);
        }

        paymentValidator.validateFailable(payment);
        paymentValidator.validatePgOrderId(payment, request.getOrderId());

        payment.fail("[" + request.getCode() + "] " + request.getMessage(), LocalDateTime.now());
        orderService.failOrder(payment.getOrder().getId());

        return PaymentFailResponse.from(payment);
    }

    /**
     * 티켓 한 장의 부분 취소 이력과 비동기 복구 작업을 접수한다.
     */
    @Transactional
    public void requestTicketPaymentCancel(Ticket ticket, String reason) {
        validatePartialCancelTarget(ticket);

        Long orderId = ticket.getOrderItem().getOrder().getId();
        Payment payment
            = paymentRepository
                .findByOrderIdWithPessimisticWriteLock(orderId)
                .orElseThrow(PaymentNotFoundException::new);

        // 기존 취소 이력은 상태에 맞게 재사용하고, 없을 때만 새로운 취소 작업을 등록한다.
        Optional<PaymentCancel> existingCancel
            = paymentCancelRepository.findByTicketIdWithPessimisticWriteLock(ticket.getId());
        if (existingCancel.isPresent()) {
            PaymentCancel paymentCancel = existingCancel.get();
            if (paymentCancel.isDone()) {
                return;
            }

            paymentValidator.validateCancelable(payment);
            reopenFailedPartialCancel(paymentCancel, reason);
            return;
        }

        paymentValidator.validateCancelable(payment);
        PaymentCancel paymentCancel
            = paymentCancelRepository.save(PaymentCancel.create(payment, ticket, reason, UUID.randomUUID().toString()));
        paymentRecoveryTaskRepository.save(PaymentRecoveryTask.createPartialCancel(paymentCancel));
    }

    /**
     * 실패한 부분 취소 이력과 복구 작업을 새로운 PG 취소 시도로 다시 활성화한다.
     */
    private void reopenFailedPartialCancel(PaymentCancel paymentCancel, String reason) {
        PaymentRecoveryTask recoveryTask
            = paymentRecoveryTaskRepository
                .findByPaymentCancel_Id(paymentCancel.getId())
                .orElseThrow(PaymentCancelStatusUnknownException::new);

        if (paymentCancel.isFailed()) {
            paymentCancel.retry(reason, UUID.randomUUID().toString());
        }
        if (recoveryTask.getStatus() == PaymentRecoveryStatus.FAILED) {
            recoveryTask.reopen();
        }
    }

    /**
     * 부분 취소 대상 티켓에서 결제와 취소 금액을 확인할 수 있는지 검증한다.
     */
    private void validatePartialCancelTarget(Ticket ticket) {
        if (ticket == null || ticket.getId() == null || ticket.getOrderItem() == null
            || ticket.getOrderItem().getOrder() == null) {
            throw new IllegalArgumentException("취소 대상 티켓과 주문 항목은 필수입니다.");
        }
    }

    /**
     * 결제 단건을 조회한다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @return 결제 상세 정보
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(PaymentNotFoundException::new);
        // 단순 조회는 락을 잡지 않고 소유자만 확인한다.
        paymentValidator.validateOwner(payment, userId);
        return PaymentResponse.from(payment);
    }

    // ===== lookup helpers =====

    /**
     * 수정이 필요한 결제를 비관적 쓰기 락으로 조회하고 소유자를 검증한다.
     */
    private Payment getOwnedPaymentWithPessimisticWriteLock(Long userId, Long paymentId) {
        Payment payment
            = paymentRepository.findByIdWithPessimisticWriteLock(paymentId).orElseThrow(PaymentNotFoundException::new);
        paymentValidator.validateOwner(payment, userId);
        return payment;
    }

    /**
     * 주문별 결제 생성 락 키를 반환한다.
     */
    private String paymentCreationLockKey(Long orderId) {
        return "payment:create:order:" + orderId;
    }

    /** 승인 보상 작업 등록 후 주문 실패 전이를 시도하고, 실패는 복구 작업에서 다시 처리할 수 있도록 기록한다. */
    private void failOrderAfterLocalApplyFailure(Long orderId) {
        try {
            orderService.failOrder(orderId);
        } catch (RuntimeException e) {
            log.error("승인 보상 작업 등록 후 주문 실패 전이 실패 - 복구 작업에서 재시도 (orderId={})", orderId, e);
        }
    }

}
