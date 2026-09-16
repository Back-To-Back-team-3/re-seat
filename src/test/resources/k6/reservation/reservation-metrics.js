// reservation 도메인 k6 시나리오가 공유하는 커스텀 메트릭 정의.

// 서버 측 세부 구간 계측(Queue-Token 검증 / 좌석 락 / DB 트랜잭션 분리)을 추가하지 않는다.
// seat_hold_duration: 클라이언트(k6)가 관측한 종단(end-to-end) 응답 시간
// 구간별 분리 계측은 T3-11(락 전략 3종 비교) 착수 시 함께 추가한다.
import {Counter, Trend} from 'k6/metrics';

// 좌석 선점 요청의 종단 응답 시간 (Queue-Token 검증 + userGameLock + seatLock + DB 트랜잭션 전체 포함)
export const seatHoldDuration = new Trend('seat_hold_duration');

// --- 정상적으로 발생할 수 있는 결과 ---
// 201 — 선점 성공
export const holdSuccess = new Counter('hold_success');
// 409 SEAT_ALREADY_HELD — 경합에서 밀린 정상 결과
export const holdSeatAlreadyHeld = new Counter('hold_seat_already_held');
// 409 LOCK_FAILED — 락 대기(3초) 초과, 경합 강도 신호이지 결함은 아님
export const holdLockFailed = new Counter('hold_lock_failed');

// --- 진짜 결함 신호 (0건이어야 over-booking 무결성 증명이 성립) ---
// 5xx 등 원인 불명
export const holdUnexpectedError = new Counter('hold_unexpected_error');

// --- 픽스처(준비 스크립트) 문제 진단용 — 정상 픽스처라면 반드시 0건이어야 한다 ---
// 0이 아니면 "동시성 결함"이 아니라 "커밋 1 준비 스크립트가 잘못됐다"는 뜻이므로 바로 구분해서 본다.
// 403 USER_NOT_VERIFIED — is_verified 시딩 실패 의심
export const holdUserNotVerified = new Counter('hold_user_not_verified');
// 400 MAX_SEAT_COUNT_EXCEEDED — 유저별 기존 보유 좌석 잔존 의심(02-reset 확인)
export const holdMaxSeatCountExceeded = new Counter('hold_max_seat_count_exceeded');
// 409 HOLD_EXTENSION_LIMIT_EXCEEDED — admission_token 재사용 의심
export const holdExtensionLimitExceeded = new Counter('hold_extension_limit_exceeded');