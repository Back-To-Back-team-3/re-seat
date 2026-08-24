package com.backtoback.reseat.domain.seatinventory.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.seatinventory.dto.GameSeatOpenResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryAlreadyOpenedException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatStatus;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.repository.SeatRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 경기 좌석 재고(game_seats) 생성 서비스.
 * <p>관리자가 경기당 1회 호출하는 비 트래픽 경로이다.
 * 구장의 활성 좌석 전체를 대상으로 PricePolicy가 산정한 가격을 부여해
 * AVAILABLE 상태의 재고를 일괄 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameSeatCreateService {

    private final GameRepository gameRepository;
    private final SeatRepository seatRepository;
    private final GameSeatRepository gameSeatRepository;
    private final PricePolicy pricePolicy;

    /**
     * 경기의 좌석 재고를 일괄 생성한다.
     *
     * @param gameId 재고를 오픈할 경기 ID
     * @return 생성 건수와 가격 범위를 담은 응답
     * @throws GameNotFoundException 경기가 존재하지 않는 경우 (404)
     * @throws SeatInventoryAlreadyOpenedException 이미 재고가 오픈된 경우 (409)
     * @throws IllegalStateException 구장에 활성 좌석이 없는 경우 (500, 기준 데이터 결함)
     */
    @Transactional
    public GameSeatOpenResponse openInventory(Long gameId) {
        // 1. 경기 조회.
        Game game = gameRepository.findDetailById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));

        // 2. 중복 오픈 방어 (1차) — 대부분의 중복 요청을 500건 INSERT 이전에 차단한다.
        if (gameSeatRepository.existsByGameId(gameId)) {
            throw new SeatInventoryAlreadyOpenedException(gameId);
        }

        Long stadiumId = game.getStadium().getId();

        // 3. 좌석 조회. zone을 fetch join해 N+1을 막는다.
        List<Seat> seats = seatRepository.findAllByStadiumIdAndStatusWithZone(stadiumId, SeatStatus.ACTIVE);
        if (seats.isEmpty()) {
            // 의도된 500: 관리자 입력 오류가 아니라 좌석 기준 데이터 결함이다. (동작 변경 없음 — 잔여 과제 참고)
            log.error("좌석 기준 데이터가 없는 구장에 재고 오픈이 시도되었습니다. gameId={}, stadiumId={}", gameId, stadiumId);
            throw new IllegalStateException("구장에 등록된 활성 좌석이 없습니다. stadiumId=" + stadiumId);
        }

        // 4. 좌석별 가격 산정 후 재고 생성
        List<GameSeat> gameSeats = seats.stream().map(seat -> createGameSeat(game, seat)).toList();

        // 5. 일괄 저장 + 2차 방어 — 사전 검사를 통과한 경합만 여기서 걸린다.
        try {
            long startedAt = System.currentTimeMillis();
            List<GameSeat> savedGameSeats = gameSeatRepository.saveAll(gameSeats);
            gameSeatRepository.flush(); // 명시적 flush로 커밋 이전에 제약 위반을 이 메서드 안에서 포착한다.
            long elapsedMillis = System.currentTimeMillis() - startedAt;

            log
                .info(
                    "좌석 재고 오픈 완료. gameId={}, stadiumId={}, createdCount={}, elapsedMs={}",
                    gameId,
                    stadiumId,
                    savedGameSeats.size(),
                    elapsedMillis
                );

            return GameSeatOpenResponse.from(gameId, savedGameSeats);
        } catch (DataIntegrityViolationException e) {
            // uk_game_seats_game_seat 위반 = 사전 검사를 함께 통과한 동시 오픈 경합.
            // 전역 핸들러에 매핑하지 않는 이유: PaymentService 등 다른 도메인의 동일 예외 처리와
            // 의미가 다르며, 전역화하면 그쪽 처리와 충돌한다.
            throw new SeatInventoryAlreadyOpenedException(gameId);
        }
    }

    /**
     * 물리 좌석 1건으로부터 경기 좌석 재고 1건을 생성한다.
     */
    private GameSeat createGameSeat(Game game, Seat seat) {
        SeatZone zone = seat.getZone();
        int price = pricePolicy.calculate(game.getGameAt(), zone.getGrade(), zone.getBasePrice());

        return GameSeat.builder().game(game).seat(seat).price(price).build();
    }
}
