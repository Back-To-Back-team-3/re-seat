package com.backtoback.reseat.domain.queue.exception;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * Kafka 대기열 진입 이벤트 발행에 실패했을 때 발생하는 예외
 */
public class QueueEventPublishFailedException extends BusinessException {

	public QueueEventPublishFailedException() {
		super(ErrorCode.QUEUE_EVENT_PUBLISH_FAILED);
	}

	public QueueEventPublishFailedException(String message) {
		super(ErrorCode.QUEUE_EVENT_PUBLISH_FAILED, message);
	}

	public QueueEventPublishFailedException(Throwable cause) {
		super(ErrorCode.QUEUE_EVENT_PUBLISH_FAILED, cause);
	}
}
