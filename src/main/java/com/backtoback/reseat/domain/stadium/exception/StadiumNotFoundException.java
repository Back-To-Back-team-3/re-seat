package com.backtoback.reseat.domain.stadium.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 구장 조회 실패 예외.
 * <p>요청한 stadiumId에 해당하는 구장이 존재하지 않을 때 발생한다.</p>
 */
public class StadiumNotFoundException extends BusinessException {

    public StadiumNotFoundException(Long stadiumId) {
        super(ErrorCode.STADIUM_NOT_FOUND, "구장을 찾을 수 없습니다. stadiumId=" + stadiumId);
    }
}
