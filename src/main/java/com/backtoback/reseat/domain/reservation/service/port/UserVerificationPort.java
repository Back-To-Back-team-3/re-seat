package com.backtoback.reseat.domain.reservation.service.port;

/**
 * 예약 도메인이 사용자 인증 상태를 조회하기 위한 계약.
 *
 * <p>User 엔티티를 직접 참조하지 않고 이 인터페이스만 의존한다.
 * 도메인 간 의존 방향을 단방향으로 유지하기 위한 포트다.
 */
public interface UserVerificationPort {

    boolean isVerified(Long userId);
}
