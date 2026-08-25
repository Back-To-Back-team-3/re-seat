package com.backtoback.reseat.domain.ticket.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.service.PaymentService;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.ticket.admin.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.exception.TicketNotFoundException;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    // 결제 취소는 PaymentRepository를 직접 조작하지 않고, PG 취소까지 포함한 실제 취소 로직을 갖는
    // PaymentService에 위임한다 (사용자 취소와 완전히 동일한 로직 재사용 + 도메인 경계 유지).
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public Page<AdminUserTicketResponse> getUserTickets(Long userId, TicketStatus status, Pageable pageable) {
        // 회원 존재 검증
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        return ticketRepository
            .findAllByUserIdAndStatusWithDetails(userId, status, pageable)
            .map(AdminUserTicketResponse::from);
    }

    // 관리자 전용: 특정 티켓 강제 취소 및 자원 반환
    @Transactional
    public AdminTicketCancelResponse cancelTicketByAdmin(Long ticketId, AdminTicketCancelRequest request) {
        // 티켓 존재 여부 조회
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);

        // 관리자 전용 취소 전이 (Status = canceled, cancelReason, cancelDetail, cancelAt 기록)
        ticket.cancelByAdmin(request.reason());

        // 사용자 취소와 동일한 취소 로직 사용
        if (ticket.getOrderItem() != null && ticket.getOrderItem().getOrder().getId() != null) {
            Long orderId = ticket.getOrderItem().getOrder().getId();
            paymentService.cancelPaymentByAdmin(orderId, new PaymentCancelRequest(request.reason()));
        }

        // 연관된 경기 좌석 자원 즉시 원복(Status = AVAILABLE, HoldExpiresAt = null)
        GameSeat gameSeat = ticket.getGameSeat();
        if (gameSeat != null) {
            gameSeat.available();
        }

        return AdminTicketCancelResponse.from(ticket);
    }
}
