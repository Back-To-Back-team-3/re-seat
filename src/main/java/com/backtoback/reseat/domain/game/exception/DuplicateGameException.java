package com.backtoback.reseat.domain.game.exception;

import java.time.LocalDateTime;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 동일 구장·동일 일시에 이미 등록된 경기가 있을 때 발생하는 예외.
 * <p>발생 경로 2가지:
 * ① AdminGameRegisterService의 existsBy 사전 검증 (일반적인 순차 중복 요청)
 * ② GameRepository.save()의 UNIQUE 제약(uk_games_stadium_game_at) 위반 (완전 동시 요청 경합 시 최종 방어선)
 */
public class DuplicateGameException extends BusinessException {

    public DuplicateGameException(Long stadiumId, LocalDateTime gameAt) {
        super(ErrorCode.DUPLICATE_GAME, "동일 구장·동일 일시에 이미 등록된 경기가 있습니다. stadiumId=" + stadiumId + ", gameAt=" + gameAt);
    }
}
