package com.backtoback.reseat.domain.payment.service;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentActionResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyConflictException;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyRequiredException;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCallbackMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelNotAllowedException;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotFoundException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotPayableException;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final Duration READY_PAYMENT_REUSE_DURATION = Duration.ofMinutes(30);
    private static final DateTimeFormatter PAYMENT_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository paymentRepository;
    // TODO: 주문 도메인 결제 조회/검증 API가 생기면 OrderRepository 직접 의존을 제거할 예정.
    private final OrderRepository orderRepository;
    private final TossPaymentClient tossPaymentClient;

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
    @Transactional
    public PaymentCreateResponse requestPayment(Long userId, String idempotencyKey, PaymentRequest request) {
        validateIdempotencyKey(idempotencyKey);

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> resolveExistingPayment(userId, request, payment))
                .orElseGet(() -> createPayment(userId, idempotencyKey, request));
    }

    /**
     * 토스 위젯 인증 완료 후 전달받은 결제 정보로 토스 승인(confirm) API를 동기 호출해 결제를 확정한다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @param request 토스가 클라이언트에 돌려준 paymentKey/orderId/amount
     * @return 확정된 결제 결과
     */
    @Transactional
    public PaymentActionResponse completePayment(Long userId, Long paymentId, PaymentCompleteRequest request) {
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        validatePaymentCanBeConfirmed(payment, request);

        TossPaymentResponse response;
        try {
            response = tossPaymentClient.confirm(
                    request.getPaymentKey(), request.getOrderId(), request.getAmount());
        } catch (RuntimeException e) {
            return recoverConfirmFailure(payment, paymentId, request.getPaymentKey(), e);
        }

        // 승인 API 응답은 받았지만 승인 완료 상태가 아니라면 로컬 결제를 실패로 닫는다.
        if (!response.isApproved()) {
            return failPaymentForUnapprovedTossConfirm(payment, paymentId, response);
        }

        return approvePaymentAfterTossConfirm(payment, paymentId, response);
    }

    /**
     * 위젯 취소 또는 실패 리다이렉트 시 결제를 실패로 기록한다. 토스 API는 호출하지 않는다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @param request 토스가 클라이언트에 돌려준 실패 code/message/orderId
     * @return 실패 처리된 결제 결과
     */
    @Transactional
    public PaymentActionResponse failPayment(Long userId, Long paymentId, PaymentFailRequest request) {
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);

        // 이미 승인/실패/취소된 결제는 실패 리다이렉트로 덮어쓰지 않는다.
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentAlreadyFinalizedException();
        }
        validatePgOrderIdMatches(payment, request.getOrderId());

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
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        validatePaymentCanBeCanceled(payment);

        TossPaymentResponse response;
        try {
            response = tossPaymentClient.cancel(payment.getPgPaymentKey(), request.getCancelReason());
        } catch (RuntimeException e) {
            return recoverCancelFailure(payment, paymentId, e);
        }

        validateTossCancelCompleted(response);

        return cancelPaymentAfterTossCancel(payment, paymentId);
    }

    /**
     * 결제 단건을 조회한다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @return 결제 상세 정보
     */
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        // 단순 조회는 락을 잡지 않고 소유자만 확인한다.
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }
        return PaymentResponse.from(payment);
    }


    // ===== request/create helpers =====

    /** 같은 멱등키 재시도 요청을 기존 결제 응답으로 반환한다. */
    private PaymentCreateResponse resolveExistingPayment(Long userId, PaymentRequest request, Payment payment) {
        validatePaymentOwner(payment, userId);
        validateIdempotencyRequestMatches(payment, request);
        return PaymentCreateResponse.from(payment);
    }

    /** 처음 보는 멱등키 요청을 새 결제 생성 흐름으로 처리한다. 만약 기존에 승인된 결제가 있으면 그것을 반환한다. */
    private PaymentCreateResponse createPayment(Long userId, String idempotencyKey, PaymentRequest request) {
        Order order = getPayableOrder(userId, request.getOrderId());

        return findApprovedPayment(order.getId())
                .map(PaymentCreateResponse::from)
                .orElseGet(() -> resolveReadyPaymentOrCreate(order, request, idempotencyKey));
    }

    /** 같은 주문의 최근 READY 결제는 재사용하고, 만료된 READY 결제는 실패로 닫은 뒤 새로 생성한다. */
    private PaymentCreateResponse resolveReadyPaymentOrCreate(Order order, PaymentRequest request, String idempotencyKey) {
        Optional<Payment> readyPayment = findReadyPayment(order.getId());
        if (readyPayment.isPresent()) {
            Payment payment = readyPayment.get();
            if (isReusableReadyPayment(payment, request)) {
                return PaymentCreateResponse.from(payment);
            }
            payment.fail("토스 결제 유효 시간이 만료되었습니다.", LocalDateTime.now());
        }

        return PaymentCreateResponse.from(createReadyPayment(order, request, idempotencyKey));
    }

    /** 토스 결제 유효 시간 안에 생성된 READY 결제인지 확인한다. */
    private boolean isReusableReadyPayment(Payment payment, PaymentRequest request) {
        LocalDateTime createdAt = payment.getCreatedAt();
        return payment.getMethod() == request.getMethod()
                && createdAt != null
                && !createdAt.isBefore(LocalDateTime.now().minus(READY_PAYMENT_REUSE_DURATION));
    }

    /** 토스 위젯 인증 전 단계의 로컬 READY 결제를 생성한다. */
    private Payment createReadyPayment(Order order, PaymentRequest request, String idempotencyKey) {
        // 토스 위젯 인증이 끝나야 승인 가능하므로, 여기서는 READY 상태로만 생성하고 승인은 콜백을 통해 completePayment에서 처리한다.
        Payment payment = Payment.builder()
                .paymentNo(generatePaymentNo())
                .orderId(order.getId())
                .user(order.getUser())
                .amount(order.getTotalAmount())
                .method(request.getMethod())
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.READY)
                .pgProvider(PgProvider.TOSS)
                .pgOrderId(order.getOrderNo())
                .build();

        return paymentRepository.save(payment);
    }

    /** 서비스 내부에서 사용할 결제 번호를 생성한다. */
    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_DATE_FORMAT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "PAY-" + timestamp + "-" + suffix;
    }

    // ===== confirm helpers =====

    /** 토스 승인 API 성공 응답을 받지 못한 경우 단건 재조회 결과로 상태를 복구한다. */
    private PaymentActionResponse recoverConfirmFailure(
            Payment payment, Long paymentId, String paymentKey, RuntimeException confirmException) {
        TossPaymentResponse tossPayment = findTossPaymentOrNull(paymentKey, paymentId);
        if (tossPayment != null) {
            if (tossPayment.isApproved()) {
                approvePaymentFromTossRequery(payment, paymentId, tossPayment);
                return PaymentActionResponse.from(payment);
            }

            // 토스 최종 상태가 실패라면 로컬 결제도 실패로 닫는다.
            if (tossPayment.isConfirmFailureStatus()) {
                log.info("토스 결제 승인 API 성공 응답 없이 재조회로 실패 상태 확인 (paymentId={}, tossStatus={})",
                        paymentId, tossPayment.getStatus());
                payment.fail("토스 결제 최종 상태가 실패입니다. status=" + tossPayment.getStatus(), LocalDateTime.now());
                return PaymentActionResponse.from(payment);
            }
        }

        // TODO: 승인 여부가 불명확한 READY 결제는 비동기 재동기화 작업에서 Toss 상태를 다시 확인한다.
        log.warn("토스 결제 승인 API 호출 실패 - 재조회로 승인 여부 확인 불가 (paymentId={})",
                paymentId, confirmException);
        throw confirmException;
    }

    /** 토스 재조회 결과가 승인 완료일 때 로컬 결제를 승인 처리한다. */
    private void approvePaymentFromTossRequery(Payment payment, Long paymentId, TossPaymentResponse tossPayment) {
        validatePgOrderIdMatches(payment, tossPayment.getOrderId());
        if (tossPayment.getTotalAmount() != null) {
            validatePaymentAmountMatches(payment, tossPayment.getTotalAmount());
        }
        payment.approve(tossPayment.getPaymentKey(), resolveApprovedAt(tossPayment.getApprovedAt()));
    }

    /** 토스 승인 API가 정상 응답했지만 승인 완료 상태가 아닐 때 로컬 결제를 실패 처리한다. */
    private PaymentActionResponse failPaymentForUnapprovedTossConfirm(
            Payment payment, Long paymentId, TossPaymentResponse response) {
        String status = response.getStatus();
        log.warn("토스 결제 승인 상태 불일치 (paymentId={}, tossStatus={})", paymentId, status);
        String failReason = status == null || status.isBlank()
                ? "토스 결제 승인 상태가 비어 있습니다."
                : "토스 결제 승인 상태가 완료가 아닙니다. status=" + response.getStatus();
        payment.fail(failReason, LocalDateTime.now());
        return PaymentActionResponse.from(payment);
    }

    /** 토스 승인 완료 응답을 받은 뒤 로컬 결제를 승인 상태로 반영한다. */
    private PaymentActionResponse approvePaymentAfterTossConfirm(
            Payment payment, Long paymentId, TossPaymentResponse response) {
        try {
            payment.approve(response.getPaymentKey(), resolveApprovedAt(response.getApprovedAt()));
        } catch (RuntimeException e) {
            log.error("토스 승인 성공 후 로컬 반영 실패 - 재동기화 필요 (paymentId={}, paymentKey={})",
                    paymentId, response.getPaymentKey(), e);
            // TODO: Toss 승인은 확정됐으므로 로컬 반영 실패 건을 비동기 재동기화 작업에서 보정한다.
            throw e;
        }
        return PaymentActionResponse.from(payment);
    }

    // ===== cancel helpers =====

    /** 토스 취소 API 성공 응답을 받지 못한 경우 단건 재조회 결과로 상태를 복구한다. */
    private PaymentActionResponse recoverCancelFailure(
            Payment payment, Long paymentId, RuntimeException cancelException) {
        TossPaymentResponse tossPayment = findTossPaymentOrNull(payment.getPgPaymentKey(), paymentId);
        // 취소 API 성공 응답은 못 받았지만, 재조회로 취소가 확인되면 정상 취소 흐름으로 복구한다.
        if (tossPayment != null && tossPayment.isCancelCompleted()) {
            log.info("토스 결제 취소 API 성공 응답 없이 재조회로 취소 확인 (paymentId={})", paymentId);
            payment.cancel();
            return PaymentActionResponse.from(payment);
        }

        // TODO: 취소 여부가 불명확한 APPROVED 결제는 비동기 재동기화 작업에서 Toss 상태를 다시 확인한다.
        log.warn("토스 결제 취소 API 호출 실패 - 재조회로 취소 확인 불가 (paymentId={})", paymentId, cancelException);
        throw new PaymentCancelFailedException(cancelException.getMessage());
    }

    /** 토스 취소 완료 응답을 받은 뒤 로컬 결제를 취소 상태로 반영한다. */
    private PaymentActionResponse cancelPaymentAfterTossCancel(Payment payment, Long paymentId) {
        // 토스 취소가 확인된 뒤에만 로컬 결제를 취소 상태로 변경한다.
        try {
            payment.cancel();
        } catch (RuntimeException e) {
            log.error("토스 취소 성공 후 로컬 반영 실패 - 재동기화 필요 (paymentId={}, paymentKey={})",
                    paymentId, payment.getPgPaymentKey(), e);
            // TODO: Toss 취소는 확정됐으므로 로컬 반영 실패 건을 비동기 재동기화 작업에서 보정한다.
            throw e;
        }

        return PaymentActionResponse.from(payment);
    }

    // ===== lookup helpers =====

    /** 수정이 필요한 결제를 비관적 쓰기 락으로 조회하고 소유자를 검증한다. */
    private Payment getOwnedPaymentWithPessimisticWriteLock(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdWithPessimisticWriteLock(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        validatePaymentOwner(payment, userId);
        return payment;
    }

    /** 토스 결제 단건 조회를 시도하고 실패하면 null을 반환한다. */
    private TossPaymentResponse findTossPaymentOrNull(String paymentKey, Long paymentId) {
        try {
            return tossPaymentClient.getPayment(paymentKey);
        } catch (RuntimeException requeryException) {
            log.warn("토스 결제 재조회 실패 (paymentId={}, paymentKey={})", paymentId, paymentKey, requeryException);
            // TODO: 단발성 재조회 실패 시 PG와 로컬 상태가 어긋날 수 있으므로 비동기 재동기화 작업에서 다시 확인한다.
            return null;
        }
    }

    /** 같은 주문에 이미 승인된 결제가 있는지 조회한다. */
    private Optional<Payment> findApprovedPayment(Long orderId) {
        return paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.APPROVED);
    }

    /** 같은 주문에 아직 승인/실패/취소되지 않은 결제 요청이 있는지 조회한다. */
    private Optional<Payment> findReadyPayment(Long orderId) {
        return paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.READY);
    }

    /** 결제 요청 가능한 주문을 조회하고 소유자와 주문 상태를 검증한다. */
    private Order getPayableOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(PaymentOrderNotFoundException::new);

        if (!order.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new PaymentOrderNotPayableException();
        }

        return order;
    }

    // ===== validation helpers =====

    /** 결제 생성 요청에 필요한 멱등키가 비어 있지 않은지 검증한다. */
    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }
    }

    /** 기존 멱등키 결제가 현재 요청의 주문/결제수단과 같은지 확인한다. */
    private void validateIdempotencyRequestMatches(Payment payment, PaymentRequest request) {
        if (!payment.getOrderId().equals(request.getOrderId())
                || payment.getMethod() != request.getMethod()) {
            throw new IdempotencyKeyConflictException();
        }
    }

    /** 토스 승인 요청 전에 로컬 결제가 승인 가능한 상태인지와 콜백 주문/금액이 일치하는지 검증한다. */
    private void validatePaymentCanBeConfirmed(Payment payment, PaymentCompleteRequest request) {
        // READY 결제만 승인 시도할 수 있으며, 토스가 돌려준 주문/금액이 로컬 결제와 맞아야 한다.
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentAlreadyFinalizedException();
        }
        validatePgOrderIdMatches(payment, request.getOrderId());
        validatePaymentAmountMatches(payment, request.getAmount());
    }

    /** 토스 취소 요청 전에 로컬 결제가 취소 가능한 상태인지 검증한다. */
    private void validatePaymentCanBeCanceled(Payment payment) {
        // PG 승인까지 끝난 결제만 취소할 수 있다.
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new PaymentCancelNotAllowedException();
        }

        if (payment.getPgPaymentKey() == null || payment.getPgPaymentKey().isBlank()) {
            throw new PaymentCancelNotAllowedException("PG 결제 키가 없어 결제를 취소할 수 없습니다.");
        }
    }

    /** 토스 취소 응답이 취소 완료 상태인지 검증한다. */
    private void validateTossCancelCompleted(TossPaymentResponse response) {
        if (!response.isCancelCompleted()) {
            String failReason = response.getStatus() == null || response.getStatus().isBlank()
                    ? "토스 결제 취소 상태가 비어 있습니다."
                    : "토스 결제 취소 상태가 완료가 아닙니다. status=" + response.getStatus();
            throw new PaymentCancelFailedException(failReason);
        }
    }

    /** 결제가 현재 사용자의 소유인지 검증한다. */
    private void validatePaymentOwner(Payment payment, Long userId) {
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }
    }

    /** 로컬 결제의 PG 주문 ID와 토스가 돌려준 주문 ID가 일치하는지 검증한다. */
    private void validatePgOrderIdMatches(Payment payment, String pgOrderId) {
        if (!payment.getPgOrderId().equals(pgOrderId)) {
            throw new PaymentCallbackMismatchException();
        }
    }

    /** 로컬 결제 금액과 토스가 돌려준 금액이 일치하는지 검증한다. */
    private void validatePaymentAmountMatches(Payment payment, Integer amount) {
        if (!payment.getAmount().equals(amount)) {
            throw new PaymentCallbackMismatchException();
        }
    }

    // ===== conversion helpers =====

    /** 토스 승인 시각 문자열을 로컬 날짜시간으로 변환하고, 값이 없으면 현재 시각을 사용한다. */
    private LocalDateTime resolveApprovedAt(String approvedAt) {
        return approvedAt != null
                ? OffsetDateTime.parse(approvedAt).toLocalDateTime()
                : LocalDateTime.now();
    }

}
