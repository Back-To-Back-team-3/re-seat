package com.backtoback.reseat.domain.game.repository;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 경기 조회용 커스텀 Repository.
 *
 * <p>경기 목록 조회는 팀, 날짜, 예매 상태 조건이 조합될 수 있으므로
 * QueryDSL 기반 동적 쿼리로 분리한다.</p>
 */
public interface GameRepositoryCustom {

    /**
     * 경기 목록을 검색 조건과 페이징 조건에 따라 조회한다.
     *
     * @param condition 경기 검색 조건
     * @param pageable  페이징 조건
     * @return 경기 목록 페이지
     */
    Page<Game> searchGames(GameSearchCondition condition, Pageable pageable);
}
