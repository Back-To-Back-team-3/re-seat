package com.backtoback.reseat.domain.game.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;

/**
 * 경기 Repository.
 * <p>기본 CRUD는 JpaRepository를 사용하고,
 * 목록 검색은 GameRepositoryCustom에서 QueryDSL로 처리한다.</p>
 */
public interface GameRepository extends JpaRepository<Game, Long>, GameRepositoryCustom {

    /**
     * 경기 상세 조회.
     * <p>상세 화면에서 홈팀, 원정팀, 구장 정보를 함께 사용하므로
     * fetch join으로 한 번에 조회해 N+1 문제를 방지한다.</p>
     *
     * @param gameId 경기 ID
     * @return 팀/구장 정보가 함께 로딩된 경기
     */
    @Query("""
        select g
        from Game g
        join fetch g.homeTeam
        join fetch g.awayTeam
        join fetch g.stadium
        where g.id = :gameId
        """)
    Optional<Game> findDetailById(@Param("gameId") Long gameId);

    /**
     * 현재 상태가 기대값과 같을 때만 지정한 상태로 바꾼다(compare-and-set).
     * 동시에 여러 요청이 와도 하나만 성공하도록 만들 때 쓴다.
     *
     * @param gameId 대상 경기 ID
     * @param expectedCurrent 호출 시점에 읽은 현재 상태
     * @param target 전이할 상태
     * @return 변경된 행 수 (성공 1, 경합 실패 0)
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Game g
        SET g.bookingStatus = :target
        WHERE g.id = :gameId
        AND g.bookingStatus = :expectedCurrent
        """)
    int compareAndSetBookingStatus(
        @Param("gameId") Long gameId,
        @Param("expectedCurrent") BookingStatus expectedCurrent,
        @Param("target") BookingStatus target
    );

    /**
     * 특정 시각 이후에 시작하는 경기 수를 센다.
     *
     * @param dateTime 기준 시각
     * @return 기준 시각 이후 경기 수
     */
    long countByGameAtAfter(LocalDateTime dateTime);
}
