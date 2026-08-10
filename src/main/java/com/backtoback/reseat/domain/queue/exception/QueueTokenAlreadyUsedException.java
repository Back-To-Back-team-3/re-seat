package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 이미 사용 완료된 Queue-Token을 다시 검증하거나 소비하려 할 때 발생하는 예외
 */
public class QueueTokenAlreadyUsedException extends BusinessException {

	public QueueTokenAlreadyUsedException() {
		super(ErrorCode.QUEUE_TOKEN_ALREADY_USED);
	}

	public QueueTokenAlreadyUsedException(String message) {
		super(ErrorCode.QUEUE_TOKEN_ALREADY_USED, message);
	}
}
