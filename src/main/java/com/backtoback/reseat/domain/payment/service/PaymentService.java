package com.backtoback.reseat.domain.payment.service;

import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentActionResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentLockFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossPaymentStatusUnknownException;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final long PAYMENT_LOCK_WAIT_SECONDS = 3L;

    private final PaymentRepository paymentRepository;
    private final PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;
    private final PaymentCreationService paymentCreationService;
    private final PaymentOrderPolicy paymentOrderPolicy;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentServiceValidator paymentValidator;
    private final RedissonClient redissonClient;

    /**
     * 주문 기준 결제를 요청한다.
     *
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
    @Transactional(noRollbackFor = OrderExpiredException.class)
    public PaymentActionResponse completePayment(
            Long userId, Long paymentId, String idempotencyKey, PaymentCompleteRequest request) {
        // 로컬 결제를 잠그고 현재 결제 시도의 콜백인지 확인한다.
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        paymentValidator.validateActiveIdempotencyKey(payment, idempotencyKey);
        if (payment.getStatus() != PaymentStatus.READY) {
            return PaymentActionResponse.from(payment);
        }

        // READY 결제만 Toss 승인 요청 전에 콜백 주문·금액을 검증한다.
        paymentValidator.validateConfirmable(payment, request.getOrderId(), request.getAmount());
        paymentOrderPolicy.ensurePayable(payment, payment.getOrder());
        payment.assignPgPaymentKey(request.getPaymentKey());

        // Toss에 최종 승인을 요청하고, 응답을 받지 못하면 클라이언트 내부에서 단건 재조회로 상태를 확인한다.
        TossPaymentResponse response;
        try {
            response = tossPaymentClient.confirm(
                    request.getPaymentKey(), request.getOrderId(), request.getAmount());
        } catch (TossPaymentStatusUnknownException e) {
            log.warn("토스 결제 승인 상태 확인 불가 - 복구 작업 등록 (paymentId={}, paymentKey={})",
                    paymentId, request.getPaymentKey(), e);
            payment.fail("토스 결제 승인 상태를 확인할 수 없습니다.", LocalDateTime.now());
            paymentRecoveryTaskRepository.save(new PaymentRecoveryTask(payment));
            return PaymentActionResponse.from(payment);
        }

        // 승인 API 응답은 받았지만 승인 완료 상태가 아니라면 로컬 결제를 실패로 닫는다.
        if (!response.isApproved()) {
            String status = response.getStatus();
            log.warn("토스 결제 승인 상태 불일치 (paymentId={}, tossStatus={})", paymentId, status);
            String failReason = status == null || status.isBlank()
                    ? "토스 결제 승인 상태가 비어 있습니다."
                    : "토스 결제 승인 상태가 완료가 아닙니다. status=" + status;
            payment.fail(failReason, LocalDateTime.now());
            return PaymentActionResponse.from(payment);
        }

        // Toss 승인이 확인됐으므로 로컬 결제에 PG 키·수단·승인 시각을 반영한다.
        payment.assignPgPaymentKey(response.getPaymentKey());
        payment.approve(response.getMethod(), resolveApprovedAt(response.getApprovedAt()));

        return PaymentActionResponse.from(payment);
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
    public PaymentActionResponse failPayment(
            Long userId, Long paymentId, String idempotencyKey, PaymentFailRequest request) {
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        paymentValidator.validateActiveIdempotencyKey(payment, idempotencyKey);
        if (payment.getStatus() != PaymentStatus.READY) {
            return PaymentActionResponse.from(payment);
        }

        paymentValidator.validateFailable(payment);
        paymentValidator.validatePgOrderId(payment, request.getOrderId());

        payment.fail("[" + request.getCode() + "] " + request.getMessage(), LocalDateTime.now());

        return PaymentActionResponse.from(payment);
    }

    /**
     * 승인된 결제를 전액 취소한다.
     *
     * <p>Toss 취소 API가 성공한 뒤에만 로컬 결제 상태를 CANCELED로 변경한다. 주문/좌석/티켓 상태 전파는 각 도메인과 합의 후 후속 작업에서 연결한다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @param request 결제 취소 요청 정보
     * @return 취소 처리된 결제 결과
     */
    @Transactional
    public PaymentActionResponse cancelPayment(Long userId, Long paymentId, PaymentCancelRequest request) {
        // 로컬 결제를 잠그고 이미 취소된 요청은 기존 결과를 반환해 멱등하게 처리한다.
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            return PaymentActionResponse.from(payment);
        }
        paymentValidator.validateCancelable(payment);

        // Toss에 전액 취소를 요청하고, 응답을 받지 못하면 클라이언트 내부에서 단건 재조회로 상태를 확인한다.
        TossPaymentResponse response;
        try {
            response = tossPaymentClient.cancel(
                    payment.getPgPaymentKey(), request.getCancelReason());
        } catch (TossPaymentStatusUnknownException e) {
            log.warn("토스 결제 취소 상태 확인 불가 (paymentId={}, paymentKey={})",
                    paymentId, payment.getPgPaymentKey(), e);
            throw new PaymentCancelFailedException("토스 결제 취소 상태를 확인할 수 없습니다.");
        }

        // Toss 응답에서도 취소 완료가 확인돼야 로컬 상태를 변경할 수 있다.
        if (!response.isCancelCompleted()) {
            String status = response.getStatus();
            String failReason = status == null || status.isBlank()
                    ? "토스 결제 취소 상태가 비어 있습니다."
                    : "토스 결제 취소 상태가 완료가 아닙니다. status=" + status;
            throw new PaymentCancelFailedException(failReason);
        }

        // 토스 취소가 확인된 뒤에만 로컬 결제를 취소 상태로 변경한다.
        payment.cancel();

        return PaymentActionResponse.from(payment);
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
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        // 단순 조회는 락을 잡지 않고 소유자만 확인한다.
        paymentValidator.validateOwner(payment, userId);
        return PaymentResponse.from(payment);
    }

    // ===== lookup helpers =====

    /** 수정이 필요한 결제를 비관적 쓰기 락으로 조회하고 소유자를 검증한다. */
    private Payment getOwnedPaymentWithPessimisticWriteLock(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdWithPessimisticWriteLock(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        paymentValidator.validateOwner(payment, userId);
        return payment;
    }

    // ===== conversion helpers =====

    /** 토스 승인 시각 문자열을 로컬 날짜시간으로 변환하고, 값이 없으면 현재 시각을 사용한다. */
    private LocalDateTime resolveApprovedAt(String approvedAt) {
        return approvedAt != null
                ? OffsetDateTime.parse(approvedAt).toLocalDateTime()
                : LocalDateTime.now();
    }

    /** 주문별 결제 생성 락 키를 반환한다. */
    private String paymentCreationLockKey(Long orderId) {
        return "payment:create:order:" + orderId;
    }

}
