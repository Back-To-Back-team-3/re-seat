package com.backtoback.reseat.domain.reservation.service.port;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.ticket.service.TicketService;

import lombok.RequiredArgsConstructor;

/**
 * TicketCountPort의 실제 구현체.
 * <p>티켓 도메인이 제공하는 조회 서비스(TicketService)에 위임한다.
 * Ticket 엔티티·TicketRepository를 직접 참조하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TicketCountAdapter implements TicketCountPort {

    private final TicketService ticketService;

    @Override
    public int countActiveTickets(Long userId, Long gameId) {
        return ticketService.countActiveTickets(userId, gameId);
    }
}
