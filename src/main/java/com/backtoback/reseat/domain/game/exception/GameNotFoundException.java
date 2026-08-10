package com.backtoback.reseat.domain.game.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 경기 조회 실패 예외.
 *
 * <p>요청한 gameId에 해당하는 경기가 존재하지 않을 때 발생한다.</p>
 */
public class GameNotFoundException extends BusinessException {

    public GameNotFoundException(Long gameId) {
        super(ErrorCode.GAME_NOT_FOUND, "경기를 찾을 수 없습니다. gameId=" + gameId);
    }
}
