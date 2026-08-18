package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 최초 좌석 선점 없이 탐색 시간이 만료된 Queue-Token인 경우 발생하는 예외
 */
public class QueueTokenBrowsingExpiredException extends BusinessException {

    public QueueTokenBrowsingExpiredException() {
        super(ErrorCode.QUEUE_TOKEN_BROWSING_EXPIRED);
    }
}
