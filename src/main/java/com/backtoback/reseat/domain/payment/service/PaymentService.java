package com.backtoback.reseat.domain.payment.service;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentActionResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyConflictException;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyRequiredException;
import com.backtoback.reseat.domain.payment.exception.InvalidOrderStatusException;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentAmountMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotFoundException;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.pg.toss.TossConfirmResponse;
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
    public PaymentResponse requestPayment(Long userId, String idempotencyKey, PaymentRequest request) {
        validateIdempotencyKey(idempotencyKey);

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> resolveExistingPayment(userId, request, payment))
                .orElseGet(() -> createPayment(userId, idempotencyKey, request));
    }

    /**
     * 주문 정보를 검증한 뒤 새 결제 요청을 생성한다.
     *
     * <p>이미 승인된 결제가 있으면 추가 결제를 생성하지 않고 기존 승인 결제 결과를 반환한다.
     *
     * @param userId 현재 사용자 ID
     * @param idempotencyKey 중복 결제 방지 키
     * @param request 결제 요청 정보
     * @return 새로 생성하거나 이미 승인된 결제 응답
     */
    private PaymentResponse createPayment(Long userId, String idempotencyKey, PaymentRequest request) {
        // 주문 조회
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(PaymentOrderNotFoundException::new);

        // 주문 검증
        validateOrder(userId, request, order);

        // 같은 주문에 승인된 결제가 있으면 새 PG 요청 없이 기존 결제 결과를 반환한다.
        Optional<Payment> approvedPayment = paymentRepository.findFirstByOrderIdAndStatus(
                order.getId(),
                PaymentStatus.APPROVED
        );
        if (approvedPayment.isPresent()) {
            return PaymentResponse.from(approvedPayment.get());
        }

        // 결제 정보 생성
        // 토스 위젯 인증이 끝나야 승인 가능하므로, 여기서는 READY 상태로만 생성하고 승인은 7.2 콜백에서 처리한다.
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

        return PaymentResponse.from(paymentRepository.save(payment));
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
        Payment payment = getOwnedPayment(userId, paymentId);

        if (payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentAlreadyFinalizedException();
        }
        validateCallbackAmount(payment, request.getOrderId(), request.getAmount());

        TossConfirmResponse response;
        try {
            response = tossPaymentClient.confirm(
                    request.getPaymentKey(), request.getOrderId(), request.getAmount());
        } catch (RuntimeException e) {
            // confirm() 호출 자체의 실패(토스 4xx/5xx 거절, 타임아웃·연결 실패, 응답 파싱 실패)만 여기서 잡아 결제 실패로 기록한다.
            // (5xx/타임아웃은 토스 쪽에서 실제로 승인됐을 가능성이 있어 재조회로 확인해야 하나 이번엔 다루지 않음 - 추후 재조회 필요)
            log.warn("토스 결제 승인 API 호출 실패 (paymentId={})", paymentId, e);
            payment.fail(e.getMessage(), LocalDateTime.now());
            return PaymentActionResponse.from(payment);
        }

        // confirm()이 정상 응답을 준 이후(=토스 승인은 이미 끝난 상태) Status가 성공이 아닐 경우
        if (!TOSS_APPROVED_STATUS.equals(response.getStatus())) {
            log.warn("토스 결제 승인 상태 불일치 (paymentId={}, tossStatus={})", paymentId, response.getStatus());
            payment.fail(resolveTossStatusFailReason(response.getStatus()), LocalDateTime.now());
            return PaymentActionResponse.from(payment);
        }

        payment.approve(response.getPaymentKey(), parseApprovedAt(response.getApprovedAt()));
        return PaymentActionResponse.from(payment);
    }

    /**
     * 위젯 취소 또는 실패 리다이렉트 시 결제를 실패로 기록한다. 토스 API는 호출하지 않는다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @param request 실패 사유(선택)
     * @return 실패 처리된 결제 결과
     */
    @Transactional
    public PaymentActionResponse failPayment(Long userId, Long paymentId, PaymentFailRequest request) {
        Payment payment = getOwnedPayment(userId, paymentId);

        // 만약 이미 확정된 결제 사항이라면 이미 완료된 결제 예외를 던짐
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentAlreadyFinalizedException();
        }
        payment.fail(resolveFailReason(request.getFailReason()), LocalDateTime.now());

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
        return PaymentResponse.from(getOwnedPayment(userId, paymentId));
    }


    // ===== private method =====

    /**
     * 결제를 조회하고 요청 사용자가 소유자인지 검증한다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @return 소유자가 확인된 결제
     */
    private Payment getOwnedPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }
        return payment;
    }

    /**
     * 콜백으로 전달된 orderId/amount가 저장된 결제 정보와 일치하는지 검증한다 (변조 방지).
     *
     * @param payment 저장된 결제
     * @param orderId 콜백으로 전달된 주문 식별자
     * @param amount 콜백으로 전달된 결제 금액
     */
    private void validateCallbackAmount(Payment payment, String orderId, Integer amount) {
        if (!payment.getPgOrderId().equals(orderId) || !payment.getAmount().equals(amount)) {
            throw new PaymentAmountMismatchException();
        }
    }

    /**
     * 실패 사유가 비어 있으면 기본 메시지로 대체한다.
     *
     * @param failReason 요청으로 전달된 실패 사유
     * @return 저장할 실패 사유
     */
    private String resolveFailReason(String failReason) {
        return (failReason == null || failReason.isBlank()) ? "결제 실패" : failReason;
    }

    /**
     * 토스 승인 응답 상태가 완료 상태가 아닐 때 저장할 실패 사유를 만든다.
     *
     * @param tossStatus 토스 결제 상태
     * @return 저장할 실패 사유
     */
    private String resolveTossStatusFailReason(String tossStatus) {
        return tossStatus == null || tossStatus.isBlank()
                ? "토스 결제 승인 상태가 비어 있습니다."
                : "토스 결제 승인 상태가 완료가 아닙니다. status=" + tossStatus;
    }

    /**
     * 토스 응답의 승인 시각 문자열을 LocalDateTime으로 변환한다.
     *
     * @param approvedAt 토스가 반환한 오프셋 포함 시각 문자열
     * @return 변환된 시각
     */
    private LocalDateTime parseApprovedAt(String approvedAt) {
        return approvedAt != null ? OffsetDateTime.parse(approvedAt).toLocalDateTime() : LocalDateTime.now();
    }

    /**
     * 동일한 Idempotency-Key로 저장된 기존 결제 요청을 처리한다.
     *
     * <p>같은 사용자, 같은 주문, 같은 금액이면 기존 결제 결과를 그대로 반환한다. 요청 정보가 다르면 멱등성 키를 잘못 재사용한 것으로
     * 판단해 예외를 던진다.
     *
     * @param userId 현재 사용자 ID
     * @param request 결제 요청 정보
     * @param payment 멱등성 키로 조회한 기존 결제
     * @return 기존 결제 응답
     */
    private PaymentResponse resolveExistingPayment(Long userId, PaymentRequest request, Payment payment) {
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }

        if (!payment.getOrderId().equals(request.getOrderId()) || !payment.getAmount().equals(request.getAmount())) {
            throw new IdempotencyKeyConflictException();
        }

        return PaymentResponse.from(payment);
    }

    /**
     * Idempotency-Key 헤더가 유효한지 검증한다.
     *
     * @param idempotencyKey 중복 결제 방지 키
     */
    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }
    }

    /**
     * 결제 요청이 주문 기준과 일치하는지 검증한다.
     *
     * <p>주문 소유자, 주문 상태, 주문 금액을 확인해 결제 가능한 주문인지 판단한다.
     *
     * @param userId 현재 사용자 ID
     * @param request 결제 요청 정보
     * @param order 결제 대상 주문
     */
    private void validateOrder(Long userId, PaymentRequest request, Order order) {
        if (!order.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStatusException();
        }

        if (order.getTotalAmount() != request.getAmount()) {
            throw new PaymentAmountMismatchException();
        }
    }

    /**
     * 내부 결제 번호를 생성한다.
     *
     * @return PAY-시각-랜덤값 형식의 결제 번호
     */
    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_DATE_FORMAT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "PAY-" + timestamp + "-" + suffix;
    }
}
