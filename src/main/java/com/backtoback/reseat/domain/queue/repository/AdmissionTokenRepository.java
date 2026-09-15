package com.backtoback.reseat.domain.queue.repository;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 대기열 재진입 판단과 토큰 상태 변경의 충돌을 막기 위해 사용자의 활성 입장 토큰을 비관적 락으로 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @param status 조회할 입장 토큰 상태
     * @return 비관적 락으로 조회한 입장 토큰 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT at
        FROM AdmissionToken at
        WHERE at.user.id = :userId
        AND at.status = :status
        """)
    List<AdmissionToken> findByUser_IdAndStatusWithPessimisticWriteLock(
        @Param("userId") Long userId,
        @Param("status") AdmissionTokenStatus status
    );

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

    /**
     * 기준 시간에 실제 사용할 수 있는 경기별 활성 Queue-Token 수를 조회한다.
     * <p>전체 만료시간과 최초 좌석 탐색 만료 조건을 함께 확인한다.</p>
     *
     * @param gameId 조회할 경기 ID
     * @param status 조회할 입장 토큰 상태
     * @param now 유효 여부를 판단할 시간
     * @return 사용할 수 있는 Queue-Token 수
     */
    @Query("""
        SELECT COUNT(at)
        FROM AdmissionToken at
        WHERE at.game.id = :gameId
        AND at.status = :status
        AND at.expiresAt > :now
        AND (
            at.seatBrowsingCompletedAt IS NOT NULL
            OR at.seatBrowsingExpiresAt > :now
        )
        """)
    long countUsableByGameId(
        @Param("gameId") Long gameId,
        @Param("status") AdmissionTokenStatus status,
        @Param("now") LocalDateTime now
    );

    /**
     * 경기와 발급 시간 범위에 해당하는 Queue-Token 수를 조회한다.
     *
     * @param gameId 조회할 경기 ID
     * @param from 포함할 조회 시작 시간
     * @param toExclusive 포함하지 않을 조회 종료 시간
     * @return 조회 기간에 발급된 Queue-Token 수
     */
    @Query("""
        SELECT COUNT(at)
        FROM AdmissionToken at
        WHERE at.game.id = :gameId
        AND at.issuedAt >= :from
        AND at.issuedAt < :toExclusive
        """)
    long countIssuedByGameIdAndPeriod(
        @Param("gameId") Long gameId,
        @Param("from") LocalDateTime from,
        @Param("toExclusive") LocalDateTime toExclusive
    );

    /**
     * 경기와 발급 시간 범위에 해당하는 Queue-Token 수를 일별로 집계한다.
     *
     * @param gameId 조회할 경기 ID
     * @param from 포함할 조회 시작 시간
     * @param toExclusive 포함하지 않을 조회 종료 시간
     * @return 날짜별 Queue-Token 발급 수
     */
    @Query(
        value = """
            SELECT CAST(at.issued_at AS DATE) AS admissionDate,
                   COUNT(*) AS admittedCount
            FROM admission_tokens at
            WHERE at.game_id = :gameId
            AND at.issued_at >= :from
            AND at.issued_at < :toExclusive
            GROUP BY CAST(at.issued_at AS DATE)
            ORDER BY CAST(at.issued_at AS DATE)
            """,
        nativeQuery = true
    )
    List<AdmissionMetricDailyProjection> findDailyAdmissionMetrics(
        @Param("gameId") Long gameId,
        @Param("from") LocalDateTime from,
        @Param("toExclusive") LocalDateTime toExclusive
    );
}
