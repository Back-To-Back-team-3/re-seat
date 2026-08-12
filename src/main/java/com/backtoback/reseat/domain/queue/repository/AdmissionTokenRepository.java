package com.backtoback.reseat.domain.queue.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;

import jakarta.persistence.LockModeType;

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
	Optional<AdmissionToken> findByTokenWithPessimisticWriteLock(@Param("token") String token);

	// 사용자의 만료되지 않은 활성 입장 토큰이 있는지 확인한다.
	boolean existsByUser_IdAndStatusAndExpiresAtAfter(Long userId, AdmissionTokenStatus status, LocalDateTime now);

	/**
	 * 대기열 이탈과 토큰 소비의 동시 상태 변경을 막기 위해 경기 · 사용자의 입장 토큰을 비관적 락으로 조회한다.
	 *
	 * @param gameId 조회할 경기 ID
	 * @param userId 조회할 사용자 ID
	 * @param status 조회할 입장 토큰 상태
	 * @return 비관적 락으로 조회한 입장 토큰
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
	    SELECT at
	    FROM AdmissionToken at
	    WHERE at.game.id = :gameId
	    AND at.user.id = :userId
	    AND at.status = :status
	    """)
	Optional<AdmissionToken> findByGame_IdAndUser_IdAndStatusWithPessimisticWriteLock(
	    @Param("gameId") Long gameId,
	    @Param("userId") Long userId,
	    @Param("status") AdmissionTokenStatus status
	);
}
