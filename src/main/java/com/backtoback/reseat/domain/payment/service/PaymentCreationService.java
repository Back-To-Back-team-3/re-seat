package com.backtoback.reseat.domain.payment.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotPayableException;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentCreationService {

    private static final DateTimeFormatter PAYMENT_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository paymentRepository;
    private final PaymentServiceValidator paymentValidator;
    private final PaymentOrderPolicy paymentOrderPolicy;

    /**
     * 멱등키와 주문의 기존 결제를 확인하고 결제 생성 또는 재사용 결과를 반환한다.
     */
    @Transactional(noRollbackFor = OrderExpiredException.class)
    public PaymentCreateResponse requestPayment(Long userId, String idempotencyKey, PaymentRequest request) {
        return paymentRepository
            .findByIdempotencyKeyWithPessimisticWriteLock(idempotencyKey)
            .map(payment -> resolveExistingPayment(userId, request, payment))
            .orElseGet(() -> requestWithNewIdempotencyKey(userId, idempotencyKey, request));
    }

    /**
     * 같은 멱등키 재시도 요청을 기존 결제 응답으로 반환한다.
     */
    private PaymentCreateResponse resolveExistingPayment(Long userId, PaymentRequest request, Payment payment) {
        paymentValidator.validateOwner(payment, userId);
        paymentValidator.validateIdempotencyRequest(payment, request.getOrderId());

        if (payment.getStatus() == PaymentStatus.READY) {
            paymentOrderPolicy.ensurePayable(payment, payment.getOrder());
        }

        return PaymentCreateResponse.from(payment);
    }

    /**
     * 처음 사용된 멱등키로 주문의 기존 결제를 확인하고, 없으면 새 결제를 생성한다.
     */
    private PaymentCreateResponse requestWithNewIdempotencyKey(
        Long userId,
        String idempotencyKey,
        PaymentRequest request
    ) {
        Order order = paymentOrderPolicy.getOwnedOrder(userId, request.getOrderId());
        Optional<Payment> existingPayment = paymentRepository.findByOrderIdWithPessimisticWriteLock(order.getId());

        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();

            if (payment.getStatus() == PaymentStatus.APPROVED) {
                return PaymentCreateResponse.from(payment);
            }

            if (payment.getStatus() != PaymentStatus.READY) {
                throw new PaymentOrderNotPayableException();
            }

            paymentOrderPolicy.ensurePayable(payment, order);
            payment.changeIdempotencyKey(idempotencyKey);
            return PaymentCreateResponse.from(payment);
        }

        paymentOrderPolicy.ensurePayable(null, order);
        return PaymentCreateResponse.from(createReadyPayment(order, idempotencyKey));
    }

    /**
     * 토스 위젯 인증 전 단계의 로컬 READY 결제를 생성한다.
     */
    private Payment createReadyPayment(Order order, String idempotencyKey) {
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_DATE_FORMAT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String paymentNo = "PAY-" + timestamp + "-" + suffix;

        Payment payment
            = Payment
                .builder()
                .paymentNo(paymentNo)
                .order(order)
                .user(order.getUser())
                .amount(order.getTotalAmount())
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.READY)
                .pgProvider(PgProvider.TOSS)
                .pgOrderId(order.getOrderNo())
                .build();

        return paymentRepository.save(payment);
    }
}
