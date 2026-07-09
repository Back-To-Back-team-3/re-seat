package com.backtoback.reseat.domain.game.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 경기 검색 조건 위반 예외.
 *
 * <p>날짜 범위가 역전되는 등 요청 파라미터 조합이 성립하지 않을 때 발생한다.
 * 서버 결함이 아니라 클라이언트 입력 오류이므로 400으로 응답한다.</p>
 */
public class InvalidGameSearchConditionException extends BusinessException {

    public InvalidGameSearchConditionException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
