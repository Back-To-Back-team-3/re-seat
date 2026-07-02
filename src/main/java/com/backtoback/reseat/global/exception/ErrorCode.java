//비즈니스 에러 코드 공통 정의(ENUM)

package com.backtoback.reseat.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //에러 코드
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보 무효/만료"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한 없음(관리자 등)"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값 오류"),
    QUEUE_TOKEN_REQUIRED(HttpStatus.FORBIDDEN, "QUEUE_TOKEN_REQUIRED", "입장 토큰 없음(대기열 우회 차단)"),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "SEAT_ALREADY_HELD", "이미 선점된 좌석입니다."),
    LOCK_FAILED(HttpStatus.CONFLICT, "LOCK_FAILED", "락 획득 실패"),
    PRE_RESERVATION_EXPIRED(HttpStatus.GONE, "PRE_RESERVATION_EXPIRED", "선점 시간 만료"),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", "중복 결제 요청"),
    PAYMENT_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "PAYMENT_TIMEOUT", "결제 제한 시간 초과"),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "정가 외 등록(재판매)"),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "아이디/이메일/닉네임/전화번호 중복"),
    RESALE_ALREADY_SOLD(HttpStatus.CONFLICT, "RESALE_ALREADY_SOLD", "이미 판매된 재판매 건");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
