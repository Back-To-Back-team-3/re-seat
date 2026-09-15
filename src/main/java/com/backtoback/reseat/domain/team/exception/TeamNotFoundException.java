package com.backtoback.reseat.domain.team.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 구단 조회 실패 예외.
 * <p>요청한 teamId에 해당하는 구단이 존재하지 않을 때 발생한다.</p>
 */
public class TeamNotFoundException extends BusinessException {

    public TeamNotFoundException(Long teamId) {
        super(ErrorCode.TEAM_NOT_FOUND, "구단을 찾을 수 없습니다. teamId=" + teamId);
    }
}
