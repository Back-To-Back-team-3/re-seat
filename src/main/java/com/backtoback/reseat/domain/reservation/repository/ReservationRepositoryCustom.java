package com.backtoback.reseat.domain.reservation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;

public interface ReservationRepositoryCustom {

    // 경기·상태별 예약 목록 페이징 조회 (관리자 전용).
    // status가 null이면 "전체" 탭.
    Page<Reservation> findByGameAndStatus(Long gameId, ReservationStatus status, Pageable pageable);
}
