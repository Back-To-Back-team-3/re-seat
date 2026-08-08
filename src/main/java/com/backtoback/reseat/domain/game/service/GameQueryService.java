package com.backtoback.reseat.domain.game.service;

import com.backtoback.reseat.domain.game.dto.GameDetailResponse;
import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경기 조회 서비스.
 *
 * <p>경기 목록/상세 조회를 담당하는 읽기 전용 서비스이다.
 * 조회 결과는 Entity가 아니라 Response DTO로 변환해 반환한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameQueryService {

    private final GameRepository gameRepository;

    /**
     * 경기 목록을 조회한다.
     *
     * @param condition 검색 조건
     * @param pageable  페이징 조건
     * @return 경기 목록 응답 페이지
     */
    public Page<GameListResponse> getGames(GameSearchCondition condition, Pageable pageable) {
        condition.validate();

        return gameRepository.searchGames(condition, pageable)
            .map(GameListResponse::from);
    }

    /**
     * 경기 상세를 조회한다.
     *
     * @param gameId 경기 ID
     * @return 경기 상세 응답
     */
    public GameDetailResponse getGame(Long gameId) {
        Game game = gameRepository.findDetailById(gameId)
            .orElseThrow(() -> new GameNotFoundException(gameId));

        return GameDetailResponse.from(game);
    }
}
