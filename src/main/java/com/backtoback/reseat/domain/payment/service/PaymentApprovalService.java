package com.backtoback.reseat.domain.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCompleteResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentConfirmStatusUnknownException;
import com.backtoback.reseat.domain.payment.exception.PaymentLocalApplyFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossPaymentStatusUnknownException;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.ticket.dto.response.TicketListResponse;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.ticket.service.TicketService;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApprovalService {

    private final PaymentRepository paymentRepository;
    private final PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;
    private final PaymentOrderPolicy paymentOrderPolicy;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentServiceValidator paymentValidator;
    private final OrderService orderService;
    private final AdmissionTokenService admissionTokenService;
    // TicketService가 PaymentService를 참조하므로 도메인 의존성을 정리하기 전까지 지연 조회한다.
    private final ObjectProvider<TicketService> ticketServiceProvider;
    private final OrderItemRepository orderItemRepository;
    private final TicketRepository ticketRepository;

    /** Toss 결제를 승인하고 결제·주문·티켓·Queue-Token 상태를 하나의 로컬 트랜잭션으로 반영한다. */
    @Transactional(
        noRollbackFor = {
            OrderExpiredException.class,
            PaymentConfirmStatusUnknownException.class
        }
    )
    public PaymentCompleteResponse approve(
        Long userId,
        Long paymentId,
        String idempotencyKey,
        String queueToken,
        PaymentCompleteRequest request
    ) {
        // 로컬 결제를 잠그고 현재 결제 시도의 콜백인지 확인한다.
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        paymentValidator.validateActiveIdempotencyKey(payment, idempotencyKey);
        if (payment.isApproved()) {
            return approvedResponse(payment);
        }
        if (!payment.isReady()) {
            throw new PaymentAlreadyFinalizedException();
        }

        // READY 결제만 Toss 승인 요청 전에 콜백 주문·금액을 검증한다.
        paymentValidator.validateConfirmable(payment, request.getOrderId(), request.getAmount());
        paymentOrderPolicy.ensurePayable(payment, payment.getOrder());
        validateQueueToken(payment, userId, queueToken);
        payment.assignQueueToken(queueToken);
        payment.assignPgPaymentKey(request.getPaymentKey());

        // Toss에 최종 승인을 요청하고, 응답을 받지 못하면 클라이언트 내부에서 단건 재조회로 상태를 확인한다.
        TossPaymentResponse response;
        try {
            response = tossPaymentClient.confirm(request.getPaymentKey(), request.getOrderId(), request.getAmount());
        } catch (TossPaymentStatusUnknownException e) {
            log
                .warn(
                    "토스 결제 승인 상태 확인 불가 - 복구 작업 등록 (paymentId={}, paymentKey={})",
                    paymentId,
                    request.getPaymentKey(),
                    e
                );
            payment.fail("토스 결제 승인 상태를 확인할 수 없습니다.", LocalDateTime.now());
            paymentRecoveryTaskRepository.save(PaymentRecoveryTask.createConfirmUnknown(payment));
            orderService.failOrder(payment.getOrder().getId());
            throw new PaymentConfirmStatusUnknownException();
        }

        // 승인 API 응답은 받았지만 승인 완료 상태가 아니라면 로컬 결제를 실패로 닫는다.
        if (!response.isApproved()) {
            String status = response.getStatus();
            log.warn("토스 결제 승인 상태 불일치 (paymentId={}, tossStatus={})", paymentId, status);
            String failReason
                = status == null || status.isBlank() ? "토스 결제 승인 상태가 비어 있습니다."
                    : "토스 결제 승인 상태가 완료가 아닙니다. status=" + status;
            payment.fail(failReason, LocalDateTime.now());
            orderService.failOrder(payment.getOrder().getId());
            consumeQueueToken(payment, userId, queueToken);
            return PaymentCompleteResponse.from(payment, List.of());
        }

        try {
            // Toss 승인이 확인됐으므로 결제·주문·티켓 상태를 로컬에 반영한다.
            payment.assignPgPaymentKey(response.getPaymentKey());
            payment.approve(response.getMethod(), resolveApprovedAt(response.getApprovedAt()));
            orderService.completeOrder(payment.getOrder().getId());
            PaymentCompleteResponse approvedResponse = approvedResponse(payment);
            consumeQueueToken(payment, userId, queueToken);
            return approvedResponse;
        } catch (RuntimeException e) {
            throw new PaymentLocalApplyFailedException(
                payment.getId(),
                payment.getOrder().getId(),
                request.getPaymentKey(),
                e
            );
        }
    }

    /** 승인 후 로컬 반영에 실패한 결제를 실패 처리하고 PG 승인 취소 작업을 등록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerApprovalCompensation(Long paymentId, String paymentKey, String queueToken) {
        Payment payment
            = paymentRepository.findByIdWithPessimisticWriteLock(paymentId).orElseThrow(PaymentNotFoundException::new);

        // 다른 승인 요청이 먼저 완료했다면 확정된 결제 상태를 보상 작업으로 덮어쓰지 않는다.
        if (!payment.isReady()) {
            return false;
        }

        payment.assignPgPaymentKey(paymentKey);
        payment.assignQueueToken(queueToken);
        payment.fail(ErrorCode.PAYMENT_LOCAL_APPLY_FAILED.getMessage(), LocalDateTime.now());
        paymentRecoveryTaskRepository.save(PaymentRecoveryTask.createApprovalCompensation(payment));
        return true;
    }

    /** 승인된 결제의 주문 항목별 티켓을 확인하고 응답한다. */
    private PaymentCompleteResponse approvedResponse(Payment payment) {
        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(payment.getOrder().getId());
        List<TicketListResponse> tickets = findIssuedTickets(orderItems);
        if (tickets.size() < orderItems.size()) {
            tickets
                = ticketServiceProvider
                    .getObject()
                    .issue(payment.getOrder())
                    .stream()
                    .map(TicketListResponse::from)
                    .toList();
        }

        return PaymentCompleteResponse.from(payment, tickets);
    }

    /** 결제에 연결된 경기와 요청 Queue-Token이 일치하는지 검증하고 사용 완료 처리한다. */
    private void consumeQueueToken(Payment payment, Long userId, String queueToken) {
        Long gameId = payment.getOrder().getReservation().getGame().getId();
        admissionTokenService.consumeToken(userId, gameId, queueToken);
    }

    /** Toss 승인 전에 Queue-Token이 결제 사용자와 예약 경기에 속하는지 검증한다. */
    private void validateQueueToken(Payment payment, Long userId, String queueToken) {
        Long gameId = payment.getOrder().getReservation().getGame().getId();
        admissionTokenService.validateToken(userId, gameId, queueToken);
    }

    /** 기존 주문 항목 조회 메서드와 티켓 단건 조회 메서드로 발급 티켓 응답을 구성한다. */
    private List<TicketListResponse> findIssuedTickets(List<OrderItem> orderItems) {
        return orderItems
            .stream()
            .map(orderItem -> ticketRepository.findByOrderItemId(orderItem.getId()))
            .flatMap(Optional::stream)
            .map(TicketListResponse::from)
            .toList();
    }

    /** 수정이 필요한 결제를 비관적 쓰기 락으로 조회하고 소유자를 검증한다. */
    private Payment getOwnedPaymentWithPessimisticWriteLock(Long userId, Long paymentId) {
        Payment payment
            = paymentRepository.findByIdWithPessimisticWriteLock(paymentId).orElseThrow(PaymentNotFoundException::new);
        paymentValidator.validateOwner(payment, userId);
        return payment;
    }

    /** 토스 승인 시각 문자열을 로컬 날짜시간으로 변환하고, 값이 없으면 현재 시각을 사용한다. */
    private LocalDateTime resolveApprovedAt(String approvedAt) {
        return approvedAt != null ? OffsetDateTime.parse(approvedAt).toLocalDateTime() : LocalDateTime.now();
    }
}
