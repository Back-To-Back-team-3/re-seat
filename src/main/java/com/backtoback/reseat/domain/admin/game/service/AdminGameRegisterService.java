package com.backtoback.reseat.domain.admin.game.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.admin.game.dto.request.GameRegisterRequest;
import com.backtoback.reseat.domain.admin.game.dto.response.GameRegisterResponse;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.InvalidBookingWindowException;
import com.backtoback.reseat.domain.game.exception.SameTeamMatchException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.exception.StadiumNotFoundException;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.exception.TeamNotFoundException;
import com.backtoback.reseat.domain.team.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 경기 등록 서비스.
 */
@Service
@RequiredArgsConstructor
public class AdminGameRegisterService {

    private final GameRepository gameRepository;
    private final TeamRepository teamRepository;
    private final StadiumRepository stadiumRepository;

    @Transactional
    public GameRegisterResponse registerGame(GameRegisterRequest request) {
        // 1. 홈팀·원정팀 동일 여부 검증 (ID 비교만으로 충분 — 조회 전에 먼저 걸러 불필요한 쿼리 방지)
        if (request.homeTeamId().equals(request.awayTeamId())) {
            throw new SameTeamMatchException(request.homeTeamId());
        }

        // 2. 팀·구장 존재 검증 + 엔티티 조회 (Game.builder()가 ID가 아닌 엔티티를 요구함)
        Team homeTeam
            = teamRepository
                .findById(request.homeTeamId())
                .orElseThrow(() -> new TeamNotFoundException(request.homeTeamId()));
        Team awayTeam
            = teamRepository
                .findById(request.awayTeamId())
                .orElseThrow(() -> new TeamNotFoundException(request.awayTeamId()));
        Stadium stadium
            = stadiumRepository
                .findById(request.stadiumId())
                .orElseThrow(() -> new StadiumNotFoundException(request.stadiumId()));

        // 3. 경기 저장 (bookingStatus는 Game 생성자 내부 기본값 SCHEDULED로 자동 설정됨)
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(request.gameAt())
                .bookingOpenAt(request.bookingOpenAt())
                .bookingCloseAt(request.bookingCloseAt())
                .title(request.title())
                .build();

        return GameRegisterResponse.from(gameRepository.save(game));
    }

    /**
     * 예매 오픈/마감 시각과 경기 일시의 순서를 검증한다.
     * <p>Bean Validation(record 컴팩트 생성자 등)으로 처리하면
     * {@code GlobalExceptionHandler.handleMethodArgumentNotValidException()}이
     * {@code errorCode.getMessage()} 고정 메시지만 응답하여 상세 사유가 소실된다.
     * 이를 피하기 위해 서비스 계층에서 {@link InvalidBookingWindowException}을 직접 던져
     * {@code handleBusinessException()}이 실제 메시지를 응답하도록 한다.
     */
    private void validateBookingWindow(GameRegisterRequest request) {
        if (!request.bookingOpenAt().isBefore(request.bookingCloseAt())) {
            throw new InvalidBookingWindowException("예매 오픈 시각은 예매 마감 시각보다 이전이어야 합니다.");
        }
        if (request.bookingCloseAt().isAfter(request.gameAt())) {
            throw new InvalidBookingWindowException("예매 마감 시각은 경기 일시 이후일 수 없습니다.");
        }
    }

}
