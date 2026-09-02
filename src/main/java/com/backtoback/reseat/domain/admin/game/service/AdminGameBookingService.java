package com.backtoback.reseat.domain.admin.game.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.exception.InvalidBookingStatusTransitionException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryNotOpenedException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자의 경기 예매 상태 전이(오픈·마감·취소)를 처리하는 서비스.
 * <p>전이 허용 여부 판단은 {@link BookingStatus} 도메인 규칙이 담당하며,
 * 이 서비스는 조회·부가 검증(좌석 재고 오픈 여부)·원자적 반영(동시 요청 경합 처리)을 책임진다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminGameBookingService {

    private final GameRepository gameRepository;
    private final GameSeatRepository gameSeatRepository;

    /**
     * 경기 예매 상태를 전이한다.
     *
     * @param gameId 전이할 경기 ID
     * @param target 목표 상태(OPEN/CLOSED/CANCELLED)
     * @param reason 전이 사유(감사 로그용, 이번 커밋에서는 저장하지 않음)
     */
    @Transactional
    public void transition(Long gameId, BookingStatus target, String reason) {

        Game game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        BookingStatus current = game.getBookingStatus();

        if (!current.canTransitionTo(target)) {
            throw new InvalidBookingStatusTransitionException(current, target);
        }

        if (target == BookingStatus.OPEN && !gameSeatRepository.existsByGameId(gameId)) {
            throw new SeatInventoryNotOpenedException(gameId);
        }

        int updated = gameRepository.compareAndSetBookingStatus(gameId, current, target);
        if (updated == 0) {
            // 조회 이후 다른 관리자가 먼저 전이시킴 — 경합 실패
            throw new InvalidBookingStatusTransitionException(current, target);
        }

        // TODO: admin_audit_logs 스키마 확정 후 감사 로그 기록 추가
    }
}
