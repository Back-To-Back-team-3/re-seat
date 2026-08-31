package com.backtoback.reseat.domain.citydata.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class StadiumCongestionNotFoundException extends BusinessException {
    public StadiumCongestionNotFoundException(Long stadiumNum) {
        super(ErrorCode.STADIUM_NOT_FOUND, "해당 구장의 혼잡도 정보를 찾을 수 없거나 지원하지 않는 구장입니다. 구장명:"+ stadiumNum);
    }
}
