package com.backtoback.reseat.domain.ticket.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.ticket.admin.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTicketService {

	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;
	private final PaymentRepository paymentRepository;

	@Transactional(readOnly = true)
	public Page<AdminUserTicketResponse> getUserTickets(Long userId, TicketStatus status, Pageable pageable) {
		// 회원 존재 검증
		if (!userRepository.existsById(userId)) {
			throw new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + userId);
		}

		return ticketRepository.findAllByUserIdAndStatusWithDetails(userId, status, pageable)
			.map(AdminUserTicketResponse::from);
	}

	//관리자 전용: 특정 티켓 강제 취소 및 자원 반환
	@Transactional
	public AdminTicketCancelResponse cancelTicketByAdmin(Long ticketId, AdminTicketCancelRequest request) {
		//티켓 존재 여부 조회
		Ticket ticket = ticketRepository.findById(ticketId)
			.orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

		//관리자 전용 취소 전이 (Status = canceled, cancelReason, cancelDatail, cancelAt 기록)
		ticket.cancelByAdmin(request.reason());

		//연관된 경기 좌석 자원 즉시 원복(Status = AVAILABLE, HoldExpiresAt = null)
		GameSeat gameSeat = ticket.getGameSeat();
		if (gameSeat != null) {
			gameSeat.available();
		}

		//연관된 결제 내역이 존재하는 경우 결제 상태 Canceled 전환
		if (ticket.getOrderItem() != null && ticket.getOrderItem().getOrder().getId() != null) {
			Long orderId = ticket.getOrderItem().getOrder().getId();
			paymentRepository.findByOrder_IdAndStatus(orderId, PaymentStatus.APPROVED)
				.ifPresent(Payment::cancel);
		}
		return AdminTicketCancelResponse.from(ticket);
	}
}
