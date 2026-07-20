package com.backtoback.reseat.domain.ticket.repository;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 내 티켓 목록에서 상태별 필터를 적용할 때 사용
    List<Ticket> findByUserIdAndStatus(Long userId, TicketStatus status);

    // 경기별 티켓 조회
    List<Ticket> findByGameId(Long gameId);

    // 티켓 번호 기반 조회
    Optional<Ticket> findByTicketNo(String ticketNo);

    // 주문 항목 기준 조회
    Optional<Ticket> findByOrderItemId(Long orderItemId);

    // 경기 좌석 기준 조회
    Optional<Ticket> findByGameSeatId(Long gameSeatId);

    // QR 토큰 기반 조회
    Optional<Ticket> findByQrToken(String qrToken);

     //관리자용: 특정 사용자의 티켓 소유 목록 조회 (Fetch Join + 동적 상태 필터 + 페이징)

    @Query(value = "select t from Ticket t " +
            "join fetch t.game g " +
            "join fetch g.stadium st " +
            "join fetch g.homeTeam ht " +
            "join fetch g.awayTeam at " +
            "join fetch t.gameSeat gs " +
            "join fetch gs.seat s " +
            "join fetch s.zone z " +
            "where t.user.id = :userId " +
            "and (:status is null or t.status = :status)",
            countQuery = "select count(t) from Ticket t " +
                    "where t.user.id = :userId " +
                    "and (:status is null or t.status = :status)")
    Page<Ticket> findAllByUserIdAndStatusWithDetails(
            @Param("userId") Long userId,
            @Param("status") TicketStatus status,
            Pageable pageable
    );
}
