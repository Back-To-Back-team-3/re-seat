package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 상태 또는 만료 시간을 기준으로 사용할 수 없는 Queue-Token인 경우 발생하는 예외
 */
public class QueueTokenExpiredException extends BusinessException {

	public QueueTokenExpiredException() {
		super(ErrorCode.QUEUE_TOKEN_EXPIRED);
	}

	public QueueTokenExpiredException(String message) {
		super(ErrorCode.QUEUE_TOKEN_EXPIRED, message);
	}
}
