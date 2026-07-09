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

    // Game
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND", "경기를 찾을 수 없습니다."),

    QUEUE_TOKEN_REQUIRED(HttpStatus.FORBIDDEN, "QUEUE_TOKEN_REQUIRED", "입장 토큰 없음(대기열 우회 차단)"),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "SEAT_ALREADY_HELD", "이미 선점된 좌석입니다."),
    LOCK_FAILED(HttpStatus.CONFLICT, "LOCK_FAILED", "락 획득 실패"),
    PRE_RESERVATION_EXPIRED(HttpStatus.GONE, "PRE_RESERVATION_EXPIRED", "선점 시간 만료"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 헤더는 필수입니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "동일한 Idempotency-Key로 다른 결제 요청을 처리할 수 없습니다."),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", "중복 결제 요청"),
    PAYMENT_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "PAYMENT_TIMEOUT", "결제 제한 시간 초과"),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PAYMENT_ACCESS_DENIED", "해당 결제 요청을 처리할 권한이 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", "결제 가능한 주문 상태가 아닙니다."),
    PAYMENT_CALLBACK_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_CALLBACK_MISMATCH", "결제 콜백 정보가 결제 내역과 일치하지 않습니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "정가 외 등록(재판매)"),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "아이디/이메일/닉네임/전화번호 중복"),
    RESALE_ALREADY_SOLD(HttpStatus.CONFLICT, "RESALE_ALREADY_SOLD", "이미 판매된 재판매 건"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 내역을 찾을 수 없습니다."),
    PAYMENT_ALREADY_FINALIZED(HttpStatus.CONFLICT, "PAYMENT_ALREADY_FINALIZED", "이미 처리 완료된 결제입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
