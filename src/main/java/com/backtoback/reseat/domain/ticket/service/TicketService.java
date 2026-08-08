package com.backtoback.reseat.domain.ticket.service;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentActionResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.service.PaymentService;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Transactional
    public List<Ticket> issue(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrder_Id(order.getId());

        return orderItems.stream()
            .filter(orderItem -> !ticketRepository.existsByOrderItemId(orderItem.getId()))
            .map(orderItem -> {
                String ticketNo = generateTicketNo(order.getCreatedAt());
                String qrToken = generateQrToken();

                Ticket ticket = Ticket.issue(
                    ticketNo,
                    order.getUser(),
                    orderItem,
                    orderItem.getGameSeat(),
                    qrToken
                );

                return ticketRepository.save(ticket);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketListResponse> getMyTickets(Long userId, TicketStatus status, Pageable pageable) {
        Page<Ticket> tickets = (status == null)
            ? ticketRepository.findByUserId(userId, pageable)
            : ticketRepository.findByUserIdAndStatus(userId, status, pageable);

        return tickets.map(TicketListResponse::from);
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId)
            .orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        return TicketDetailResponse.from(ticket);
    }

    @Transactional
    public TicketCancelResponse cancelTicket(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findDetailById(ticketId)
            .orElseThrow(TicketNotFoundException::new);

        if (!ticket.getUser().getId().equals(userId)) {
            throw new TicketAccessDeniedException();
        }

        validateUserCancelable(ticket);

        ticket.cancel(TicketCancelReason.USER_REFUND);

        Payment payment = getApprovedPaymentByOrderId(ticket.getOrderItem().getOrder().getId());

        PaymentActionResponse paymentResponse = paymentService.cancelPayment(
            userId,
            payment.getId(),
            new PaymentCancelRequest("사용자 티켓 취소")
        );

        boolean refunded = paymentResponse.getStatus() == PaymentStatus.CANCELED;

        GameSeat gameSeat = ticket.getGameSeat();
        gameSeat.available();

        return TicketCancelResponse.of(ticket, refunded, null);
    }

    @Transactional
    public TicketVerifyResponse verify(Long gameId, String qrToken) {
        Ticket ticket = ticketRepository.findByQrTokenAndGameId(qrToken, gameId)
            .orElseThrow(TicketNotFoundException::new);

        ticket.markUsed();

        return TicketVerifyResponse.from(ticket);
    }

    private void validateUserCancelable(Ticket ticket) {
        LocalDateTime cancelDeadline = ticket.getGame().getGameAt().minusHours(24);
        if (LocalDateTime.now().isAfter(cancelDeadline)) {
            throw new TicketCancelDeadlinePassedException();
        }
    }

    private Payment getApprovedPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrder_IdAndStatus(orderId, PaymentStatus.APPROVED)
            .orElseGet(() -> paymentRepository.findByOrderIdWithPessimisticWriteLock(orderId)
                .orElseThrow(PaymentNotFoundException::new));
    }

    private String generateTicketNo(LocalDateTime baseTime) {
        String datePart = baseTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String ticketNo = "TKT-" + datePart + "-" + UUID.randomUUID().toString()
            .replace("-", "")
            .substring(0, 6)
            .toUpperCase();

        int retry = 0;
        while (ticketRepository.existsByTicketNo(ticketNo) && retry < 3) {
            ticketNo = "TKT-" + datePart + "-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
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
