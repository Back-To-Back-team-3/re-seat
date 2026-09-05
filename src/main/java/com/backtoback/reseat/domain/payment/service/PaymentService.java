package com.backtoback.reseat.domain.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCancelResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCompleteResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentFailResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentCancel;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus; // [신규] 관리자 강제취소 시 APPROVED 결제 조회용
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelResponseInvalidException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelStatusUnknownException;
import com.backtoback.reseat.domain.payment.exception.PaymentLockFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossPaymentStatusUnknownException;
import com.backtoback.reseat.domain.payment.repository.PaymentCancelRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.ticket.dto.response.TicketListResponse;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.ticket.service.TicketService;

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
    private final PaymentOrderPolicy paymentOrderPolicy;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentServiceValidator paymentValidator;
    private final RedissonClient redissonClient;
    private final OrderService orderService;
    // TicketService가 PaymentService를 참조하므로 도메인 의존성을 정리하기 전까지 지연 조회한다.
    private final ObjectProvider<TicketService> ticketServiceProvider;
    private final OrderItemRepository orderItemRepository;
    private final TicketRepository ticketRepository;

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
    @Transactional(noRollbackFor = OrderExpiredException.class)
    public PaymentCompleteResponse completePayment(
        Long userId,
        Long paymentId,
        String idempotencyKey,
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
            return PaymentCompleteResponse.from(payment, List.of());
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
            return PaymentCompleteResponse.from(payment, List.of());
        }

        // Toss 승인이 확인됐으므로 로컬 결제에 PG 키·수단·승인 시각을 반영한다.
        payment.assignPgPaymentKey(response.getPaymentKey());
        payment.approve(response.getMethod(), resolveApprovedAt(response.getApprovedAt()));
        orderService.completeOrder(payment.getOrder().getId());

        return approvedResponse(payment);
    }

    /**
     * 승인된 결제의 주문 항목별 티켓을 확인하고 응답한다.
     */
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

    /**
     * 기존 주문 항목 조회 메서드와 티켓 단건 조회 메서드로 발급 티켓 응답을 구성한다.
     */
    private List<TicketListResponse> findIssuedTickets(List<OrderItem> orderItems) {
        return orderItems
            .stream()
            .map(orderItem -> ticketRepository.findByOrderItemId(orderItem.getId()))
            .flatMap(Optional::stream)
            .map(TicketListResponse::from)
            .toList();
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
     * 승인된 결제를 전액 취소한다. (사용자 본인 취소 전용)
     * <p>Toss 취소 API가 성공한 뒤에만 로컬 결제와 주문을 취소 상태로 변경한다.
     * <p>실제 취소 처리는 {@link #cancelApprovedPayment(Payment, PaymentCancelRequest)}에 위임
     * 이 메서드는 "본인 소유 결제인지" 조회·검증하는 책임만 가진다.
     *
     * @param userId 현재 사용자 ID
     * @param paymentId 결제 ID
     * @param request 결제 취소 요청 정보
     * @return 취소 처리된 결제 결과
     */
    @Transactional
    public PaymentCancelResponse cancelPayment(Long userId, Long paymentId, PaymentCancelRequest request) {
        // 로컬 결제를 잠그고 소유자를 검증한다. (사용자는 본인 결제만 취소 가능)
        Payment payment = getOwnedPaymentWithPessimisticWriteLock(userId, paymentId);
        return cancelApprovedPayment(payment, request);
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
     * 관리자 강제취소 전용
     * <p>사용자 취소({@link #cancelPayment(Long, Long, PaymentCancelRequest)})와 달리 결제 소유자 검증을 하지 않음
     * 관리자는 특정 회원 본인이 아니라 "주문" 단위로 강제취소를 수행, paymentId 대신 orderId로 APPROVED 상태의 결제를 찾아 취소
     * <p>PG 취소 성공 확인, 로컬 결제/주문 상태 동기화 등 취소의 핵심 로직은 사용자 취소와 완전히 동일하게
     * {@link #cancelApprovedPayment(Payment, PaymentCancelRequest)}를 재사용한다.
     *
     * @param orderId 취소 대상 결제가 속한 주문 ID
     * @param request 취소 사유 등 취소 요청 정보
     */
    @Transactional
    public void cancelPaymentByAdmin(Long orderId, PaymentCancelRequest request) {
        paymentRepository
            .findByOrder_IdAndStatus(orderId, PaymentStatus.APPROVED)
            // 위 조회는 락 없이 존재 여부만 확인하므로, 실제 취소 처리 전에 비관적 쓰기 락으로 다시 잠가
            // 사용자 취소(getOwnedPaymentWithPessimisticWriteLock)와 동일한 동시성 안전성을 보장한다.
            .flatMap(payment -> paymentRepository.findByIdWithPessimisticWriteLock(payment.getId()))
            .ifPresent(payment -> cancelApprovedPayment(payment, request));
    }

    /**
     * 결제 취소의 공통 핵심 로직.
     * <p>PG 취소 API 호출 → 취소 완료 여부 확인 → (성공 시에만) 로컬 결제/주문 상태 반영 순서로 처리한다.
     * 사용자 취소와 관리자 강제취소가 이 메서드를 공유함으로써, 관리자 경로에서 PG 호출을 생략해
     * "DB는 취소인데 실제 환불은 되지 않는" 정합성 문제가 재발하지 않도록 한다.
     *
     * @param payment 비관적 쓰기 락으로 조회된, 취소 대상 결제
     * @param request 취소 사유 등 취소 요청 정보
     * @return 취소 처리된 결제 결과
     */
    private PaymentCancelResponse cancelApprovedPayment(Payment payment, PaymentCancelRequest request) {
        // 이미 취소된 요청은 기존 결과를 반환해 멱등하게 처리한다.
        if (payment.isCanceled()) {
            return PaymentCancelResponse.from(payment);
        }
        paymentValidator.validateCancelable(payment);

        // Toss에 전액 취소를 요청하고, 응답을 받지 못하면 클라이언트 내부에서 단건 재조회로 상태를 확인한다.
        TossPaymentResponse response;
        try {
            response = tossPaymentClient.cancel(payment.getPgPaymentKey(), request.getCancelReason());
        } catch (TossPaymentStatusUnknownException e) {
            log.warn("토스 결제 취소 상태 확인 불가 (paymentId={}, paymentKey={})", payment.getId(), payment.getPgPaymentKey(), e);
            throw new PaymentCancelStatusUnknownException();
        }

        // Toss 응답에서도 취소 완료가 확인돼야 로컬 상태를 변경할 수 있다.
        if (!response.isCancelCompleted()) {
            log
                .warn(
                    "토스 결제 취소 응답 상태 불일치 (paymentId={}, paymentKey={}, status={})",
                    payment.getId(),
                    payment.getPgPaymentKey(),
                    response.getStatus()
                );
            throw new PaymentCancelResponseInvalidException();
        }

        // 토스 취소가 확인된 뒤에만 로컬 결제와 주문을 함께 취소 상태로 변경한다.
        payment.cancel();
        orderService.cancelPaidOrder(payment.getOrder().getId());

        // 같은 주문에 남아있는 나머지 ISSUED 티켓도 함께 취소 (티켓 ISSUED로 남는 불일치 해결)
        // 3차 : 좌석 단위 부분 환불 도입 시 이 일괄 취소는 제거하고 취소 대상 티켓만 처리하도록 변경.
        cancelRemainingIssuedTickets(payment.getOrder().getId());

        return PaymentCancelResponse.from(payment);
    }

    /**
     * 같은 주문에 속한 나머지 ISSUED 티켓을 결제 취소 사유로 일괄 취소
     * <p>주문 단위 전액 취소만 지원하는 동안, 방금 취소된 티켓 외 나머지 티켓이
     * "좌석은 반환됐는데 티켓은 ISSUED로 남는" 상태가 되는 것을 막기 위한 임시 방어 로직
     * <p>Ticket.cancel()은 티켓 상태만 변경하므로, 좌석 반환은 여기서 같은 트랜잭션 안에
     * 명시적으로 함께 처리한다(다른 메서드의 좌석 반환 로직에 암묵적으로 의존하지 않는다).
     */
    private void cancelRemainingIssuedTickets(Long orderId) {
        orderItemRepository
            .findByOrder_Id(orderId)
            .stream()
            .map(orderItem -> ticketRepository.findByOrderItemId(orderItem.getId()))
            .flatMap(Optional::stream)
            .filter(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
            .forEach(ticket -> ticket.cancel(TicketCancelReason.PAYMENT_CANCELED));
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

    // ===== conversion helpers =====

    /**
     * 토스 승인 시각 문자열을 로컬 날짜시간으로 변환하고, 값이 없으면 현재 시각을 사용한다.
     */
    private LocalDateTime resolveApprovedAt(String approvedAt) {
        return approvedAt != null ? OffsetDateTime.parse(approvedAt).toLocalDateTime() : LocalDateTime.now();
    }

    /**
     * 주문별 결제 생성 락 키를 반환한다.
     */
    private String paymentCreationLockKey(Long orderId) {
        return "payment:create:order:" + orderId;
    }

}
