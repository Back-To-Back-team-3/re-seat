package com.backtoback.reseat.domain.game.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 홈팀과 원정팀이 동일한 경기를 등록하려는 경우 발생하는 예외.
 */
public class SameTeamMatchException extends BusinessException {

    public SameTeamMatchException(Long teamId) {
        super(ErrorCode.SAME_TEAM_MATCH, "홈팀과 원정팀이 동일할 수 없습니다. teamId=" + teamId);
    }
}
