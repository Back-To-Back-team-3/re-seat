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
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelFailedException;
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
    private final PaymentServiceValidator paymentValidator;

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
        paymentValidator.validateIdempotencyKey(idempotencyKey);

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> resolveExistingPayment(userId, request, payment))
                .orElseGet(() -> requestWithNewIdempotencyKey(userId, idempotencyKey, request));
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
    @Transactional
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

        // Toss에 최종 승인을 요청하고, 응답을 받지 못하면 클라이언트 내부에서 단건 재조회로 상태를 확인한다.
        TossPaymentResponse response = tossPaymentClient.confirm(
                request.getPaymentKey(), request.getOrderId(), request.getAmount());

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
        payment.approve(response.getPaymentKey(), response.getMethod(), resolveApprovedAt(response.getApprovedAt()));

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
        TossPaymentResponse response = tossPaymentClient.cancel(
                payment.getPgPaymentKey(), request.getCancelReason());

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
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        // 단순 조회는 락을 잡지 않고 소유자만 확인한다.
        paymentValidator.validateOwner(payment, userId);
        return PaymentResponse.from(payment);
    }


    // ===== request/create helpers =====

    /** 같은 멱등키 재시도 요청을 기존 결제 응답으로 반환한다. */
    private PaymentCreateResponse resolveExistingPayment(Long userId, PaymentRequest request, Payment payment) {
        paymentValidator.validateOwner(payment, userId);
        paymentValidator.validateIdempotencyRequest(payment, request.getOrderId());
        return PaymentCreateResponse.from(payment);
    }

    /** 처음 사용된 멱등키로 승인 결제나 재사용 가능한 READY 결제를 확인하고, 없으면 새 결제를 생성한다. */
    private PaymentCreateResponse requestWithNewIdempotencyKey(
            Long userId, String idempotencyKey, PaymentRequest request) {
        // 주문 소유자와 결제 가능 상태를 확인한 뒤 주문 기준으로 기존 결제를 조회한다.
        Order order = getPayableOrder(userId, request.getOrderId());

        // 같은 주문에 승인된 결제가 있으면 새 PG 요청 없이 기존 결제 결과를 반환한다.
        Optional<Payment> approvedPayment = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
                order.getId(), PaymentStatus.APPROVED);
        if (approvedPayment.isPresent()) {
            return PaymentCreateResponse.from(approvedPayment.get());
        }

        // 아직 유효한 READY 결제가 있으면 새 결제를 만들지 않고 기존 요청을 재사용한다.
        Optional<Payment> readyPayment = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
                order.getId(), PaymentStatus.READY);
        if (readyPayment.isPresent()) {
            Payment payment = readyPayment.get();
            LocalDateTime createdAt = payment.getCreatedAt();
            boolean reusable = createdAt != null
                    && !createdAt.isBefore(LocalDateTime.now().minus(READY_PAYMENT_REUSE_DURATION));

            if (reusable) {
                payment.changeIdempotencyKey(idempotencyKey);
                return PaymentCreateResponse.from(payment);
            }

            // 재사용 기한이 지난 READY 결제는 실패로 닫고 아래에서 새 결제를 생성한다.
            payment.fail("토스 결제 유효 시간이 만료되었습니다.", LocalDateTime.now());
        }

        return PaymentCreateResponse.from(createReadyPayment(order, idempotencyKey));
    }

    /** 토스 위젯 인증 전 단계의 로컬 READY 결제를 생성한다. */
    private Payment createReadyPayment(Order order, String idempotencyKey) {
        // 서비스 내부에서 사용할 결제 번호 생성
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_DATE_FORMAT);
        String suffix = UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 8);
        String paymentNo = "PAY-" + timestamp + "-" + suffix;

        // 토스 위젯 인증이 끝나야 승인 가능하므로, 여기서는 READY 상태로만 생성하고 승인은 콜백을 통해 completePayment에서 처리한다.
        Payment payment = Payment.builder()
                              .paymentNo(paymentNo)
                              .orderId(order.getId())
                              .user(order.getUser())
                              .amount(order.getTotalAmount())
                              .idempotencyKey(idempotencyKey)
                              .status(PaymentStatus.READY)
                              .pgProvider(PgProvider.TOSS)
                              .pgOrderId(order.getOrderNo())
                              .build();

        return paymentRepository.save(payment);
    }

    // ===== lookup helpers =====

    /** 수정이 필요한 결제를 비관적 쓰기 락으로 조회하고 소유자를 검증한다. */
    private Payment getOwnedPaymentWithPessimisticWriteLock(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdWithPessimisticWriteLock(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        paymentValidator.validateOwner(payment, userId);
        return payment;
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

    // ===== conversion helpers =====

    /** 토스 승인 시각 문자열을 로컬 날짜시간으로 변환하고, 값이 없으면 현재 시각을 사용한다. */
    private LocalDateTime resolveApprovedAt(String approvedAt) {
        return approvedAt != null
                ? OffsetDateTime.parse(approvedAt).toLocalDateTime()
                : LocalDateTime.now();
    }

}
