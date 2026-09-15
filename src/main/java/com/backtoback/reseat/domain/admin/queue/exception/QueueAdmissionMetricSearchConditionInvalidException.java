package com.backtoback.reseat.domain.admin.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 관리자 입장 지표 조회 조건이 올바르지 않은 경우 발생하는 예외.
 */
public class QueueAdmissionMetricSearchConditionInvalidException extends BusinessException {

    public QueueAdmissionMetricSearchConditionInvalidException() {
        super(ErrorCode.QUEUE_ADMISSION_METRIC_SEARCH_CONDITION_INVALID);
    }
}
