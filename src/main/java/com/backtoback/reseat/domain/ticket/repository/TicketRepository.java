package com.backtoback.reseat.domain.ticket.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUserIdAndStatus(Long userId, TicketStatus status);

    List<Ticket> findByGameId(Long gameId);

    Optional<Ticket> findByTicketNo(String ticketNo);

    Optional<Ticket> findByOrderItemId(Long orderItemId);

    Optional<Ticket> findByGameSeatId(Long gameSeatId);

    Optional<Ticket> findByQrToken(String qrToken);

    Optional<Ticket> findByQrTokenAndGameId(String qrToken, Long gameId);

    boolean existsByOrderItemId(Long orderItemId);

    boolean existsByTicketNo(String ticketNo);

    boolean existsByQrToken(String qrToken);

    Page<Ticket> findByUserId(Long userId, Pageable pageable);

    Page<Ticket> findByUserIdAndStatus(Long userId, TicketStatus status, Pageable pageable);

    /**
     * 사용자 본인 티켓 단건 조회 (상세)
     * - 사용자 취소 API
     * - 사용자 상세 조회 API
     * 에서 연관 엔티티(경기, 좌석 등)를 Fetch Join으로 함께 조회할 때 사용
     */
    @Query("select t from Ticket t " +
        "join fetch t.user u " +
        "join fetch t.game g " +
        "join fetch g.stadium st " +
        "join fetch g.homeTeam ht " +
        "join fetch g.awayTeam at " +
        "join fetch t.gameSeat gs " +
        "join fetch gs.seat s " +
        "join fetch s.zone z " +
        "where t.id = :id")
    Optional<Ticket> findDetailById(@Param("id") Long id);

    /**
     * 사용자 본인 티켓 단건 조회
     * - 사용자 취소 API
     * - 사용자 상세 조회 API
     * 에서 본인 소유 티켓인지 검증할 때 사용
     */
    Optional<Ticket> findByIdAndUserId(Long ticketId, Long userId);

    /**
     * 관리자용: 특정 사용자의 티켓 소유 목록 조회
     * Fetch Join + 동적 상태 필터 + 페이징
     */
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
