package com.backtoback.reseat.domain.game.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
