package com.backtoback.reseat.domain.game.repository;

import com.backtoback.reseat.domain.game.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {
    // 좌석 조회·경기 검색 커스텀 쿼리는 이후에 추가
}
