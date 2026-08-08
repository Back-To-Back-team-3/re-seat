package com.backtoback.reseat.domain.seatinventory.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.seatinventory.dto.ZoneSummaryResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryNotOpenedException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 경기별 좌석 현황·구역 요약 조회 서비스.
 *
 * <p>재고 생성은 GameSeatCreateService가 담당하고,
 * 이 클래스는 조회만 담당한다.
 *
 * <p>메서드 readOnly = true
 * 동시 접속자가 많은 경로라 N+1 쿼리가 없는지가 가장 중요하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatQueryService {

    private final GameRepository gameRepository;
    private final GameSeatRepository gameSeatRepository;

    /**
     * 경기의 좌석 현황을 조회한다.
     *
     * @param gameId 경기 ID
     * @param zoneId 구역 ID (null이면 전체)
     * @param grade  좌석 등급 (null이면 전체)
     * @param status 좌석 상태 (null이면 전체)
     * @throws GameNotFoundException           경기가 없을 때 (404)
     * @throws SeatInventoryNotOpenedException 재고가 아직 오픈되지 않았을 때 (409)
     */
    public List<SeatStatusResponse> getSeats(
        Long gameId, Long zoneId, SeatGrade grade, GameSeatStatus status) {

        validateGame(gameId);

        List<GameSeat> gameSeats = gameSeatRepository.findAllByGameIdWithFilters(
            gameId, zoneId, grade, status);

        if (gameSeats.isEmpty()) {
            // 필터 결과가 0건이면 두 가지 경우가 있다:
            // 1. 재고 자체가 없는 경우
            // 2. 필터 조건에 맞는 좌석만 없는 경우
            // existsByGameId로 재고 유무를 다시 확인해 구분한다.
            if (!gameSeatRepository.existsByGameId(gameId)) {
                throw new SeatInventoryNotOpenedException(gameId);
            }
            // 재고는 있고 필터 조건만 안 맞는 경우 → 빈 리스트 반환 (404 아님)
        }

        return gameSeats.stream()
            .map(SeatStatusResponse::from)
            .toList();
    }

    /**
     * 경기의 구역별 잔여 좌석 수를 집계한다.
     *
     * @param gameId 경기 ID
     * @throws GameNotFoundException           경기가 없을 때 (404)
     * @throws SeatInventoryNotOpenedException 재고가 아직 오픈되지 않았을 때 (409)
     */
    public List<ZoneSummaryResponse> getZoneSummaries(Long gameId) {

        Game game = gameRepository.findDetailById(gameId)
            .orElseThrow(() -> new GameNotFoundException(gameId));

        if (!gameSeatRepository.existsByGameId(gameId)) {
            throw new SeatInventoryNotOpenedException(gameId);
        }

        Long stadiumId = game.getStadium().getId();
        return gameSeatRepository.findZoneSummariesByGameId(gameId, stadiumId);
    }

    private void validateGame(Long gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new GameNotFoundException(gameId);
        }
    }
}
