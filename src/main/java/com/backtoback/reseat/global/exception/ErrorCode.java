//비즈니스 에러 코드 공통 정의(ENUM)

package com.backtoback.reseat.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통 (global)
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않거나 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수가 초과되었습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 연동 서버와의 통신에 실패했습니다."),

    // 사용자 (users)
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디/이메일/닉네임/전화번호입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "비활성 또는 정지된 계정입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다."),
    ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "관리자 권한 계정만 로그인할 수 있습니다."),
    TICKET_EXISTS_ON_WITHDRAWAL(HttpStatus.CONFLICT, "정산 미완료 티켓이 존재하여 탈퇴할 수 없습니다."),
    // 본인인증
    USER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "본인인증이 완료되지 않은 계정입니다."),
    VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "본인인증 처리에 실패했습니다."),
    VERIFICATION_DUPLICATE_CI(HttpStatus.CONFLICT, "이미 동일한 명의로 가입된 다른 계정이 존재합니다."),

    // 기준 데이터 (teams, stadiums, seat_zones)
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "구단을 찾을 수 없습니다."),
    STADIUM_NOT_FOUND(HttpStatus.NOT_FOUND, "구장을 찾을 수 없습니다."),
    ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석 구역을 찾을 수 없습니다."),

    // 경기 (games)
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다."),
    SAME_TEAM_MATCH(HttpStatus.BAD_REQUEST, "홈팀과 원정팀이 동일할 수 없습니다."),
    BOOKING_NOT_OPEN(HttpStatus.CONFLICT, "예매 가능한 상태가 아닙니다."),
    INVALID_BOOKING_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 예매 상태 전이입니다."),

    // 대기열 (queue)
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "이미 해당 경기의 대기열에 진입해 있습니다."),
    QUEUE_TOKEN_REQUIRED(HttpStatus.FORBIDDEN, "입장 토큰이 필요합니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.FORBIDDEN, "유효하지 않은 입장 토큰입니다."),
    QUEUE_TOKEN_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 입장 토큰입니다."),
    QUEUE_TOKEN_EXPIRED(HttpStatus.GONE, "만료된 입장 토큰입니다."),
    QUEUE_TOKEN_REVOKED(HttpStatus.GONE, "취소된 입장 토큰입니다."),
    QUEUE_TOKEN_BROWSING_EXPIRED(HttpStatus.GONE, "최초 좌석 탐색 시간이 만료된 입장 토큰입니다."),
    QUEUE_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "대기열 진입 이력이 없습니다."),
    QUEUE_ENTRY_CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT, "대기 중 혹은 입장 허용된 상태만 취소할 수 있습니다."),
    QUEUE_ENTRY_ADMISSION_NOT_ALLOWED(HttpStatus.CONFLICT, "대기 중인 상태만 입장 허용할 수 있습니다."),
    QUEUE_ENTRY_REENTRY_NOT_ALLOWED(HttpStatus.CONFLICT, "취소된 상태만 대기열에 재진입할 수 있습니다."),
    QUEUE_ENTRY_CANCELLATION_TOKEN_REQUIRED(HttpStatus.CONFLICT, "입장 허용 상태는 활성 입장 토큰과 함께 취소해야 합니다."),
    QUEUE_INVALID_STATUS(HttpStatus.CONFLICT, "유효하지 않은 상태입니다."),
    QUEUE_TOKEN_NOT_EXPIRED(HttpStatus.CONFLICT, "아직 만료되지 않은 입장 토큰입니다."),
    QUEUE_TOKEN_BROWSING_NOT_EXPIRED(HttpStatus.CONFLICT, "아직 좌석 탐색 시간이 만료되지 않은 입장 토큰입니다."),
    QUEUE_REGISTRATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Redis ZSet 대기열 등록에 실패했습니다."),
    QUEUE_EVENT_PUBLISH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Kafka 대기열 진입 이벤트 발행에 실패했습니다."),
    QUEUE_ENTRY_EVENT_REQUIRED(HttpStatus.BAD_REQUEST, "대기열 진입 이벤트가 비어 있습니다."),
    QUEUE_ENTRY_EVENT_ID_REQUIRED(HttpStatus.BAD_REQUEST, "대기열 진입 이벤트 ID가 누락되었습니다."),
    QUEUE_ENTRY_EVENT_GAME_ID_INVALID(HttpStatus.BAD_REQUEST, "대기열 진입 이벤트의 경기 ID가 올바르지 않습니다."),
    QUEUE_ENTRY_EVENT_USER_ID_INVALID(HttpStatus.BAD_REQUEST, "대기열 진입 이벤트의 사용자 ID가 올바르지 않습니다."),
    QUEUE_ENTRY_EVENT_REQUESTED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "대기열 진입 요청 시간이 누락되었습니다."),
    QUEUE_ADMISSION_INTERRUPTED(HttpStatus.SERVICE_UNAVAILABLE, "입장 허용 처리 중 스레드가 중단되었습니다."),
    QUEUE_REDIS_MEMBER_INVALID(HttpStatus.SERVICE_UNAVAILABLE, "Redis 대기열 사용자 정보가 올바르지 않습니다."),

    // 좌석 (seats, game_seats)
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
    GAME_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "경기 좌석을 찾을 수 없습니다."),
    GAME_SEAT_NOT_IN_GAME(HttpStatus.BAD_REQUEST, "요청한 좌석이 해당 경기에 속하지 않습니다."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점된 좌석입니다."),
    SEAT_ALREADY_SOLD(HttpStatus.CONFLICT, "이미 판매된 좌석입니다."),
    SEAT_BLOCKED(HttpStatus.CONFLICT, "판매가 차단된 좌석입니다."),
    SEAT_INVENTORY_NOT_OPENED(HttpStatus.CONFLICT, "좌석 재고가 아직 오픈되지 않았습니다."),
    SEAT_INVENTORY_ALREADY_OPENED(HttpStatus.CONFLICT, "이미 좌석 재고가 오픈된 경기입니다."),
    MAX_SEAT_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "1인당 최대 예매 좌석 수를 초과했습니다."),
    LOCK_FAILED(HttpStatus.CONFLICT, "잠시 후 다시 시도해 주세요."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 상태 전이입니다."),

    // 예약/선점 (reservations)
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 예약에 접근할 권한이 없습니다."),
    INVALID_RESERVATION_STATUS(HttpStatus.CONFLICT, "요청을 처리할 수 있는 예약 상태가 아닙니다."),
    RESERVATION_ALREADY_ORDERED(HttpStatus.CONFLICT, "이미 주문이 생성된 예약입니다."),
    PRE_RESERVATION_EXPIRED(HttpStatus.GONE, "좌석 선점 시간이 만료되었습니다."),
    RESERVATION_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 좌석을 찾을 수 없습니다."),

    // 주문 (orders)
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "결제 가능한 주문 상태가 아닙니다."),
    ORDER_EXPIRED(HttpStatus.GONE, "결제 제한 시각이 경과했습니다."),

    // 결제 (payments)
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다."),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 결제 요청을 처리할 권한이 없습니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "요청 금액이 주문 금액과 일치하지 않습니다."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더는 필수입니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "동일한 Idempotency-Key로 다른 결제 요청을 처리할 수 없습니다."),
    IDEMPOTENCY_KEY_UNAVAILABLE(HttpStatus.CONFLICT, "만료되었거나 사용할 수 없는 Idempotency-Key입니다."),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "중복된 결제 요청입니다."),
    PAYMENT_ALREADY_FINALIZED(HttpStatus.CONFLICT, "이미 처리 완료된 결제입니다."),
    PAYMENT_CALLBACK_MISMATCH(HttpStatus.BAD_REQUEST, "결제 콜백 정보가 결제 내역과 일치하지 않습니다."),
    PAYMENT_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "결제 제한 시간이 초과되었습니다."),
    PAYMENT_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 없는 결제 상태입니다."),
    PAYMENT_CANCEL_FAILED(HttpStatus.BAD_GATEWAY, "결제 취소 처리에 실패했습니다."),

    // 티켓 (tickets)
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "티켓을 찾을 수 없습니다."),
    TICKET_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 티켓에 접근할 권한이 없습니다."),
    TICKET_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 티켓입니다."),
    TICKET_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 티켓입니다."),
    TICKET_CANCEL_DEADLINE_PASSED(HttpStatus.CONFLICT, "취소 가능 시점이 경과했습니다."),

    // 재판매 (resales) — 2차 확장
    RESALE_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "재판매 게시글을 찾을 수 없습니다."),
    RESALE_ALREADY_LISTED(HttpStatus.CONFLICT, "이미 재판매 등록된 티켓입니다."),
    RESALE_ALREADY_SOLD(HttpStatus.CONFLICT, "이미 판매된 재판매 건입니다."),
    RESALE_SELF_PURCHASE(HttpStatus.BAD_REQUEST, "본인이 등록한 게시글은 구매할 수 없습니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "정가 외의 가격으로 등록할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    // 응답 바디의 errorCode 값, enum 상수명과 항상 동일 name()으로 대체
    public String getCode() {
        return name();
    }
}
