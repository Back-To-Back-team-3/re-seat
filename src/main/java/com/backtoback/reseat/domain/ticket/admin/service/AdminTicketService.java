package com.backtoback.reseat.domain.ticket.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.ticket.admin.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.ticket.service.TicketService;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    // 강제 취소의 실제 오케스트레이션(환불 파이프라인 진입·검증)은 TicketService가 진입점이자 검증 주체로 담당한다.
    // 사용자 취소와 동일한 로직 재사용 + 결제/좌석/주문 상태 전이 일관성 유지
    private final TicketService ticketService;

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

    // 관리자 전용: 특정 티켓 강제 취소 및 반환
    @Transactional
    public AdminTicketCancelResponse cancelTicketByAdmin(Long ticketId, AdminTicketCancelRequest request) {
        return ticketService.cancelTicketByAdmin(ticketId, request.reason());
    }
}
