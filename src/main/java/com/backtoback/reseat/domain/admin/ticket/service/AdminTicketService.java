package com.backtoback.reseat.domain.admin.ticket.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.admin.ticket.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.admin.ticket.dto.request.TicketSearchCondition;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketBulkCancelResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketBulkCancelResult;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketQrReissueResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
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

    // 관리자 전용: QR 토큰 재발급
    @Transactional
    public AdminTicketQrReissueResponse reissueQrToken(Long ticketId) {
        return ticketService.reissueQrToken(ticketId);
    }

    // 관리자 전용: 회원·날짜·상태 통합 검색
    @Transactional(readOnly = true)
    public Page<AdminUserTicketResponse> searchTickets(TicketSearchCondition condition, Pageable pageable) {
        if (condition.userId() != null && !userRepository.existsById(condition.userId())) {
            throw new UserNotFoundException();
        }

        return ticketRepository.searchTickets(condition, pageable).map(AdminUserTicketResponse::from);
    }

    /**
     * 관리자 전용: 경기 단위로 ISSUED 티켓을 일괄 취소한다.
     * <p>이 메서드 자체는 트랜잭션을 걸지 않는다.
     * 티켓 하나씩 ticketService.cancelTicketByAdmin()을 호출해 각 호출이 독립된 트랜잭션으로 커밋·롤백되게 하기 위함이다.
     *
     * @Transactional을 붙이면 모든 호출이 하나의 트랜잭션으로 묶여서 티켓 한 장 실패로 나머지까지 롤백될 수 있다.</p>
     */
    public AdminTicketBulkCancelResponse cancelTicketsByGame(Long gameId, String reason) {
        List<Ticket> targets = ticketRepository.findByGameIdAndStatus(gameId, TicketStatus.ISSUED);

        List<AdminTicketBulkCancelResult> results = new ArrayList<>();
        for (Ticket ticket : targets) {
            try {
                ticketService.cancelTicketByAdmin(ticket.getId(), reason);
                results.add(AdminTicketBulkCancelResult.success(ticket.getId()));
            } catch (RuntimeException e) {
                results.add(AdminTicketBulkCancelResult.failure(ticket.getId(), e.getMessage()));
            }
        }

        return AdminTicketBulkCancelResponse.of(results);
    }
}
