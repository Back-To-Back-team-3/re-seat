package com.backtoback.reseat.domain.payment.service;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentMethod;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyConflictException;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyRequiredException;
import com.backtoback.reseat.domain.payment.exception.InvalidOrderStatusException;
import com.backtoback.reseat.domain.payment.exception.PaymentAmountMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotFoundException;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.pg.mock.MockPaymentClient;
import com.backtoback.reseat.domain.payment.pg.mock.MockPaymentResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final DateTimeFormatter PAYMENT_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository paymentRepository;
    // TODO: 주문 도메인 결제 조회/검증 API가 생기면 OrderRepository 직접 의존을 제거할 예정.
    private final OrderRepository orderRepository;
    private final MockPaymentClient mockPaymentClient;

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
        Payment payment = Payment.builder()
                .paymentNo(generatePaymentNo())
                .orderId(order.getId())
                .user(order.getUser())
                .amount(order.getTotalAmount())
                .method(resolveMethod(request.getMethod()))
                .idempotencyKey(idempotencyKey)
                .pgProvider(PgProvider.MOCK)
                .pgOrderId(order.getOrderNo())
                .build();

        // MVP에서는 Mock PG가 즉시 승인/실패 결과를 반환한다고 가정한다.
        MockPaymentResult result = mockPaymentClient.requestPayment();

        if (result.approved()) {
            payment.approve(result.pgPaymentKey(), result.requestedAt());
        } else {
            payment.fail(result.failReason(), result.requestedAt());
        }

        return PaymentResponse.from(paymentRepository.save(payment));
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
     * 요청 결제 수단을 결정한다.
     *
     * <p>요청에 결제 수단이 없으면 MVP 기본값인 MOCK을 사용한다.
     *
     * @param method 요청 결제 수단
     * @return 저장할 결제 수단
     */
    private PaymentMethod resolveMethod(PaymentMethod method) {
        return method != null ? method : PaymentMethod.MOCK;
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
