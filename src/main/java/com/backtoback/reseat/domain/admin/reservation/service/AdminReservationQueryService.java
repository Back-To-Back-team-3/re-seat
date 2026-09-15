package com.backtoback.reseat.domain.admin.reservation.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.admin.reservation.dto.response.AdminReservationListResponse;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.global.common.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final GameRepository gameRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminReservationListResponse> getReservations(
        Long gameId,
        ReservationStatus status,
        Pageable pageable
    ) {
        // 존재하지 않는 gameId → 404 GAME_NOT_FOUND
        if (!gameRepository.existsById(gameId)) {
            throw new GameNotFoundException(gameId);
        }

        Page<Reservation> reservationPage = reservationRepository.findByGameAndStatus(gameId, status, pageable);

        List<Long> reservationIds = reservationPage.getContent().stream().map(Reservation::getId).toList();

        // 2차 배치 조회: 컬렉션 fetch join + 페이징 충돌을 피하기 위해 예약 조회와 분리
        Map<Long, List<ReservationSeat>> seatsByReservationId
            = reservationSeatRepository
                .findWithGameSeatByReservationIdIn(reservationIds)
                .stream()
                .collect(Collectors.groupingBy(rs -> rs.getReservation().getId()));

        LocalDateTime now = LocalDateTime.now();

        Page<AdminReservationListResponse> responsePage
            = reservationPage
                .map(
                    reservation -> AdminReservationListResponse
                        .of(reservation, seatsByReservationId.getOrDefault(reservation.getId(), List.of()), now)
                );

        return PageResponse.of(responsePage);
    }
}
