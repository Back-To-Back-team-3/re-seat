package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 입장 토큰의 저장과 조회를 담당하는 Repository
 */
public interface AdmissionTokenRepository extends JpaRepository<AdmissionToken, Long> {

    // 경기, 사용자, 토큰 상태가 일치하고 기준 시간 이후까지 유효한 입장 토큰을 조회한다.
    Optional<AdmissionToken> findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
            Long gameId,
            Long userId,
            AdmissionTokenStatus status,
            LocalDateTime now
    );

    // 토큰 값으로 상태와 만료 여부에 관계없이 입장 토큰을 조회한다.
    Optional<AdmissionToken> findByToken(String token);

    // 토큰 값으로 입장 토큰을 조회하고 소비 처리가 끝날 때까지 비관적 쓰기 잠금을 유지한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdmissionToken a where a.token = :token")
    Optional<AdmissionToken> findByTokenWithPessimisticWriteLock(
            @Param("token") String token
    );
}
