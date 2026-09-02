package com.backtoback.reseat.domain.citydata.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

public class CityDataApiException extends BusinessException {
    public CityDataApiException() {
        super(ErrorCode.EXTERNAL_API_ERROR);
    }

    public CityDataApiException(String message) {
        super(ErrorCode.EXTERNAL_API_ERROR, message);
    }

}
