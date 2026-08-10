package com.backtoback.reseat.domain.payment.pg.toss.exception;

import lombok.Getter;

@Getter
public class TossApiException extends RuntimeException {

	private final int statusCode;
	private final String responseBody;

	/**
	 * Toss API의 오류 상태와 응답 본문을 보존하는 내부 연동 예외를 생성한다.
	 */
	public TossApiException(String operation, int statusCode, String responseBody) {
		super("토스페이먼츠 결제 " + operation + " API 호출 실패"
			+ " (status=" + statusCode + ", response=" + responseBody + ")");
		this.statusCode = statusCode;
		this.responseBody = responseBody;
	}
}
