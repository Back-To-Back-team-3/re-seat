package com.backtoback.reseat.domain.ticket.repository;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 티켓 번호 기반 조회
    // uk_tickets_no(ticket_no) 유니크 제약 활용
    Optional<Ticket> findByTicketNo(String ticketNo);

    // 주문 항목 기준 조회
    // uk_tickets_order_item(order_item_id) 유니크 제약 활용
    Optional<Ticket> findByOrderItemId(Long orderItemId);

    // 경기 좌석 기준 조회
    // uk_tickets_game_seat(game_seat_id) 유니크 제약 활용
    Optional<Ticket> findByGameSeatId(Long gameSeatId);

    // QR 토큰 기반 조회
    // uk_tickets_qr_token(qr_token) 유니크 제약 활용
    Optional<Ticket> findByQrToken(String qrToken);

    // 내 티켓 목록에서 상태별 필터를 적용할 때 사용
    // idx_tickets_user_status(user_id, status) 인덱스 활용
    List<Ticket> findByUserIdAndStatus(Long userId, TicketStatus status);

    // 경기별 티켓 조회
    // idx_tickets_game(game_id) 인덱스 활용
    List<Ticket> findByGameId(Long gameId);
}
