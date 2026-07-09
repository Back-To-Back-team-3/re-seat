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

    private static final DateTimeFormatter PAYMENT_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TOSS_APPROVED_STATUS = "DONE";
    private static final String TOSS_CANCELED_STATUS = "CANCELED";
    private static final String TOSS_PARTIAL_CANCELED_STATUS = "PARTIAL_CANCELED";
    private static final String TOSS_ABORTED_STATUS = "ABORTED";
    private static final String TOSS_EXPIRED_STATUS = "EXPIRED";

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
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> {
                    if (!payment.getUser().getId().equals(userId)) {
                        throw new PaymentAccessDeniedException();
                    }

                    if (!payment.getOrderId().equals(request.getOrderId())
                            || payment.getMethod() != request.getMethod()) {
                        throw new IdempotencyKeyConflictException();
                    }

                    return PaymentCreateResponse.from(payment);
                })
                .orElseGet(() -> {
                    // 주문 조회
                    Order order = orderRepository.findById(request.getOrderId())
                            .orElseThrow(PaymentOrderNotFoundException::new);

                    // 주문 검증
                    if (!order.getUser().getId().equals(userId)) {
                        throw new PaymentAccessDeniedException();
                    }

                    if (order.getStatus() != OrderStatus.CREATED) {
                        throw new PaymentOrderNotPayableException();
                    }

                    // 같은 주문에 승인된 결제가 있으면 새 PG 요청 없이 기존 결제 결과를 반환한다.
                    Optional<Payment> approvedPayment = paymentRepository.findFirstByOrderIdAndStatus(
                            order.getId(),
                            PaymentStatus.APPROVED
                    );
                    if (approvedPayment.isPresent()) {
                        return PaymentCreateResponse.from(approvedPayment.get());
                    }

                    // 결제 정보 생성
                    // 토스 위젯 인증이 끝나야 승인 가능하므로, 여기서는 READY 상태로만 생성하고 승인은 7.2 콜백에서 처리한다.
                    String timestamp = LocalDateTime.now().format(PAYMENT_NO_DATE_FORMAT);
                    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

                    Payment payment = Payment.builder()
                            .paymentNo("PAY-" + timestamp + "-" + suffix)
                            .orderId(order.getId())
                            .user(order.getUser())
                            .amount(order.getTotalAmount())
                            .method(request.getMethod())
                            .idempotencyKey(idempotencyKey)
                            .status(PaymentStatus.READY)
                            .pgProvider(PgProvider.TOSS)
                            .pgOrderId(order.getOrderNo())
                            .build();

                    return PaymentCreateResponse.from(paymentRepository.save(payment));
                });
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

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentAlreadyFinalizedException();
        }
        if (!payment.getPgOrderId().equals(request.getOrderId())
                || !payment.getAmount().equals(request.getAmount())) {
            throw new PaymentCallbackMismatchException();
        }

        TossPaymentResponse response;
        try {
            response = tossPaymentClient.confirm(
                    request.getPaymentKey(), request.getOrderId(), request.getAmount());
        } catch (RuntimeException e) {
            log.warn("토스 결제 승인 API 호출 실패 (paymentId={})", paymentId, e);
            TossPaymentResponse tossPayment = findTossPaymentOrNull(request.getPaymentKey(), paymentId);
            if (tossPayment != null && TOSS_APPROVED_STATUS.equals(tossPayment.getStatus())) {
                if (!payment.getPgOrderId().equals(tossPayment.getOrderId())
                        || (tossPayment.getTotalAmount() != null
                        && !payment.getAmount().equals(tossPayment.getTotalAmount()))) {
                    throw new PaymentCallbackMismatchException();
                }
                payment.approve(tossPayment.getPaymentKey(), resolveApprovedAt(tossPayment.getApprovedAt()));
                return PaymentActionResponse.from(payment);
            }

            if (tossPayment != null
                    && (TOSS_ABORTED_STATUS.equals(tossPayment.getStatus())
                    || TOSS_EXPIRED_STATUS.equals(tossPayment.getStatus())
                    || TOSS_CANCELED_STATUS.equals(tossPayment.getStatus()))) {
                payment.fail("토스 결제 최종 상태가 실패입니다. status=" + tossPayment.getStatus(), LocalDateTime.now());
                return PaymentActionResponse.from(payment);
            }

            // 승인 여부가 불명확한 경우에는 로컬 상태를 FAILED로 바꾸지 않는다.
            // 사용자가 같은 paymentKey로 재시도하거나 운영자가 Toss 조회 후 수동 보정할 수 있게 READY 상태를 유지한다.
            throw e;
        }

        // confirm()이 정상 응답을 준 이후(=토스 승인은 이미 끝난 상태) Status가 성공이 아닐 경우
        if (!TOSS_APPROVED_STATUS.equals(response.getStatus())) {
            log.warn("토스 결제 승인 상태 불일치 (paymentId={}, tossStatus={})", paymentId, response.getStatus());
            String failReason = response.getStatus() == null || response.getStatus().isBlank()
                    ? "토스 결제 승인 상태가 비어 있습니다."
                    : "토스 결제 승인 상태가 완료가 아닙니다. status=" + response.getStatus();
            payment.fail(failReason, LocalDateTime.now());
            return PaymentActionResponse.from(payment);
        }

        try {
            payment.approve(response.getPaymentKey(), resolveApprovedAt(response.getApprovedAt()));
        } catch (RuntimeException e) {
            log.error("토스 승인 성공 후 로컬 반영 실패 - 재조회 필요 (paymentId={}, paymentKey={})",
                    paymentId, response.getPaymentKey(), e);
            throw e;
        }
        return PaymentActionResponse.from(payment);
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

        // 만약 이미 확정된 결제 사항이라면 이미 완료된 결제 예외를 던짐
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentAlreadyFinalizedException();
        }
        if (!payment.getPgOrderId().equals(request.getOrderId())) {
            throw new PaymentCallbackMismatchException();
        }

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

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new PaymentCancelNotAllowedException();
        }

        if (payment.getPgPaymentKey() == null || payment.getPgPaymentKey().isBlank()) {
            throw new PaymentCancelNotAllowedException("PG 결제 키가 없어 결제를 취소할 수 없습니다.");
        }

        TossPaymentResponse response;
        try {
            response = tossPaymentClient.cancel(payment.getPgPaymentKey(), request.getCancelReason());
        } catch (RuntimeException e) {
            log.warn("토스 결제 취소 API 호출 실패 (paymentId={})", paymentId, e);
            TossPaymentResponse tossPayment = findTossPaymentOrNull(payment.getPgPaymentKey(), paymentId);
            if (tossPayment != null
                    && (TOSS_CANCELED_STATUS.equals(tossPayment.getStatus())
                    || TOSS_PARTIAL_CANCELED_STATUS.equals(tossPayment.getStatus()))) {
                payment.cancel();
                return PaymentActionResponse.from(payment);
            }
            throw new PaymentCancelFailedException(e.getMessage());
        }

        if (!TOSS_CANCELED_STATUS.equals(response.getStatus())
                && !TOSS_PARTIAL_CANCELED_STATUS.equals(response.getStatus())) {
            String failReason = response.getStatus() == null || response.getStatus().isBlank()
                    ? "토스 결제 취소 상태가 비어 있습니다."
                    : "토스 결제 취소 상태가 완료가 아닙니다. status=" + response.getStatus();
            throw new PaymentCancelFailedException(failReason);
        }

        try {
            payment.cancel();
        } catch (RuntimeException e) {
            log.error("토스 취소 성공 후 로컬 반영 실패 - 재조회 필요 (paymentId={}, paymentKey={})",
                    paymentId, payment.getPgPaymentKey(), e);
            throw e;
        }

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
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }
        return PaymentResponse.from(payment);
    }


    // ===== private method =====

    private Payment getOwnedPaymentWithPessimisticWriteLock(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdWithPessimisticWriteLock(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }
        return payment;
    }

    private TossPaymentResponse findTossPaymentOrNull(String paymentKey, Long paymentId) {
        try {
            return tossPaymentClient.getPayment(paymentKey);
        } catch (RuntimeException requeryException) {
            log.warn("토스 결제 재조회 실패 (paymentId={}, paymentKey={})", paymentId, paymentKey, requeryException);
            // TODO: 단발성 재조회 실패 시 PG와 로컬 상태가 어긋날 수 있으므로 비동기 재동기화 작업을 추가할 필요성이 있습니다.
            return null;
        }
    }

    private LocalDateTime resolveApprovedAt(String approvedAt) {
        return approvedAt != null
                ? OffsetDateTime.parse(approvedAt).toLocalDateTime()
                : LocalDateTime.now();
    }

}
