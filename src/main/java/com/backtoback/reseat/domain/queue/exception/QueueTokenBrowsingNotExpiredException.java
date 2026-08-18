package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 좌석 탐색 시간이 지나지 않은 Queue-Token을 탐색 만료 처리하려는 경우 발생하는 예외
 */
public class QueueTokenBrowsingNotExpiredException extends BusinessException {

    public QueueTokenBrowsingNotExpiredException() {
        super(ErrorCode.QUEUE_TOKEN_BROWSING_NOT_EXPIRED);
    }
}
