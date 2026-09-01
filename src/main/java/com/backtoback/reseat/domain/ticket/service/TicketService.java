package com.backtoback.reseat.domain.ticket.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCancelResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.service.PaymentService;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketCancelResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketDetailResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketListResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketVerifyResponse;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.exception.TicketAccessDeniedException;
import com.backtoback.reseat.domain.ticket.exception.TicketCancelDeadlinePassedException;
import com.backtoback.reseat.domain.ticket.exception.TicketNotFoundException;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    // Order 패키지가 이미 병합한 티켓 단위 부분 환불 엔트리 포인트(OrderService#refundOrder)를 그대로 재사용한다.
    private final OrderService orderService;

    @Transactional
    public List<Ticket> issue(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(order.getId());

        return orderItems
            .stream()
            .filter(orderItem -> !ticketRepository.existsByOrderItemId(orderItem.getId()))
            .map(orderItem -> {
                String ticketNo = generateTicketNo(order.getCreatedAt());
                String qrToken = generateQrToken();

                Ticket ticket = Ticket.issue(ticketNo, order.getUser(), orderItem, orderItem.getGameSeat(), qrToken);

                return ticketRepository.save(ticket);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketListResponse> getMyTickets(Long userId, TicketStatus status, Pageable pageable) {
        Page<Ticket> tickets
            = (status == null) ? ticketRepository.findByUserId(userId, pageable)
            : ticketRepository.findByUserIdAndStatus(userId, status, pageable);

        return tickets.map(TicketListResponse::from);
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId).orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        return TicketDetailResponse.from(ticket);
    }

    /**
     * 사용자 본인의 티켓 1장을 취소한다.
     * <p>진입점이자 검증 주체로서 소유자·상태(ISSUED)·환불 기한을 검증한 뒤 REFUND_PENDING으로 전이
     * PG 호출과 결제 상태 갱신은 {@link PaymentService}에 위임
     * 결제가 성공하면 티켓을 REFUNDED로 확정
     * 이미 병합된 {@link OrderService#refundOrder(Long)}로 주문 항목·좌석·주문 상태를 반영
     * 결제 호출이 실패하면 티켓을 REFUND_FAILED로 되돌린다(이 경우 좌석·주문은 변경하지 않는다).</p>
     *
     * <p>결제 패키지({@code PaymentService#cancelPayment})가 아직 "주문 전체" 취소만 지원하는 동안
     * 같은 주문에 다른 ISSUED 티켓이 남아 있어도 함께 전액 환불된다(기존 동작과 동일).
     * 결제 패키지가 티켓 단위 부분 취소(ticketId, cancelAmount)를 지원하도록 바뀌면
     * 이 메서드는 그대로 두고 결제 호출부만 자연스럽게 "티켓 1장만 환불"로 좁혀진다.</p>
     *
     * @param userId 현재 사용자 ID
     * @param ticketId 취소할 티켓 ID
     * @return 취소 처리 결과
     */
    @Transactional
    public TicketCancelResponse cancelTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId).orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        // 환불 기한은 ISSUED 상태일 때만 의미가 있다. 그 외 상태는 requestRefund()가 구체적인 예외로 구분해 던진다.
        if (ticket.getStatus() == TicketStatus.ISSUED && !ticket.isRefundable()) {
            throw new TicketCancelDeadlinePassedException();
        }

        ticket.requestRefund(TicketCancelReason.USER_REFUND, null);

        Order order = ticket.getOrderItem().getOrder();
        Payment payment = getApprovedPaymentByOrderId(order.getId());

        PaymentCancelResponse paymentResponse;
        try {
            paymentResponse = paymentService.cancelPayment(userId, payment.getId(), new PaymentCancelRequest("사용자 티켓 취소"));
        } catch (RuntimeException e) {
            ticket.failRefund();
            throw e;
        }

        ticket.completeRefund();
        orderService.refundOrder(ticket.getOrderItem().getId());

        return TicketCancelResponse
            .of(ticket, paymentResponse, order.getStatus(), buildRemainingTickets(order.getId(), ticket.getId()));
    }

    /**
     * 관리자 강제 취소 전용. 소유자·환불 기한 검증 없이 지정한 티켓을 강제로 환불 파이프라인에 태운다.
     *
     * @param ticketId 강제 취소할 티켓 ID
     * @param detail 관리자가 입력한 취소 상세 사유
     * @return 관리자 강제 취소 응답
     */
    @Transactional
    public AdminTicketCancelResponse cancelTicketByAdmin(Long ticketId, String detail) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);

        ticket.requestRefund(TicketCancelReason.ADMIN_FORCE_CANCEL, detail);

        Long orderId = ticket.getOrderItem().getOrder().getId();
        String reason = (detail != null && !detail.isBlank()) ? detail : "관리자 직권 취소";

        try {
            paymentService.cancelPaymentByAdmin(orderId, new PaymentCancelRequest(reason));
        } catch (RuntimeException e) {
            ticket.failRefund();
            throw e;
        }

        ticket.completeRefund();
        orderService.refundOrder(ticket.getOrderItem().getId());

        return AdminTicketCancelResponse.from(ticket);
    }

    /**
     * REFUND_FAILED 상태의 티켓 취소를 재시도한다.
     * <p>새 이력을 만들지 않고 같은 티켓을 다시 REFUND_PENDING으로 되돌린 뒤 결제를 다시 호출한다.
     * REFUND_FAILED가 아닌 티켓에 호출하면 {@link com.backtoback.reseat.domain.ticket.exception.InvalidTicketStateException}이 발생한다.</p>
     *
     * @param userId 현재 사용자 ID
     * @param ticketId 재시도할 티켓 ID
     * @return 취소 처리 결과
     */
    @Transactional
    public TicketCancelResponse retryCancelTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId).orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        ticket.retryRefund();

        Order order = ticket.getOrderItem().getOrder();
        Payment payment = getApprovedPaymentByOrderId(order.getId());

        PaymentCancelResponse paymentResponse;
        try {
            paymentResponse
                = paymentService.cancelPayment(userId, payment.getId(), new PaymentCancelRequest("사용자 티켓 취소 재시도"));
        } catch (RuntimeException e) {
            ticket.failRefund();
            throw e;
        }

        ticket.completeRefund();
        orderService.refundOrder(ticket.getOrderItem().getId());

        return TicketCancelResponse
            .of(ticket, paymentResponse, order.getStatus(), buildRemainingTickets(order.getId(), ticket.getId()));
    }

    @Transactional
    public TicketVerifyResponse verify(Long gameId, String qrToken) {
        Ticket ticket
            = ticketRepository.findByQrTokenAndGameId(qrToken, gameId).orElseThrow(TicketNotFoundException::new);

        ticket.markEntered();

        return TicketVerifyResponse.from(ticket);
    }

    /**
     * 같은 주문에 남아 있는 다른 티켓들의 현재 상태 요약을 만든다. (방금 취소한 티켓은 제외)
     */
    private List<TicketCancelResponse.RemainingTicket> buildRemainingTickets(Long orderId, Long excludeTicketId) {
        return orderItemRepository
            .findByOrder_Id(orderId)
            .stream()
            .map(orderItem -> ticketRepository.findByOrderItemId(orderItem.getId()))
            .flatMap(Optional::stream)
            .filter(t -> !t.getId().equals(excludeTicketId))
            .map(t -> new TicketCancelResponse.RemainingTicket(t.getId(), t.getStatus()))
            .toList();
    }

    /**
     * 주문 ID로 취소 가능한(APPROVED) 결제를 조회한다.
     * <p>기존 취소 흐름과 동일하게, 락 없는 조회가 실패하면 비관적 쓰기 락으로 한 번 더 조회한다.</p>
     */
    private Payment getApprovedPaymentByOrderId(Long orderId) {
        return paymentRepository
            .findByOrder_IdAndStatus(orderId, PaymentStatus.APPROVED)
            .orElseGet(
                () -> paymentRepository
                    .findByOrderIdWithPessimisticWriteLock(orderId)
                    .orElseThrow(PaymentNotFoundException::new)
            );
    }

    private String generateTicketNo(LocalDateTime baseTime) {
        String datePart = baseTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String ticketNo
            = "TKT-" + datePart + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        int retry = 0;
        while (ticketRepository.existsByTicketNo(ticketNo) && retry < 3) {
            ticketNo
                = "TKT-" + datePart + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            retry++;
        }

        return ticketNo;
    }

    private String generateQrToken() {
        String qrToken = UUID.randomUUID().toString();
        int retry = 0;

        while (ticketRepository.existsByQrToken(qrToken) && retry < 3) {
            qrToken = UUID.randomUUID().toString();
            retry++;
        }

        return qrToken;
    }
}
