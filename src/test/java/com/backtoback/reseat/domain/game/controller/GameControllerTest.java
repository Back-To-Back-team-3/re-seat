package com.backtoback.reseat.domain.game.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.backtoback.reseat.domain.game.dto.GameDetailResponse;
import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.exception.InvalidGameSearchConditionException;
import com.backtoback.reseat.domain.game.service.GameQueryService;
import com.backtoback.reseat.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 경기 조회 Controller 테스트.
 *
 * <p>Controller 계층의 요청 매핑, 응답 래핑, 예외 응답을 검증한다.
 * Service와 Repository는 Mock으로 대체해 Redis, DB 등 외부 인프라 의존성을 제거한다.</p>
 */
@WebMvcTest(GameController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameQueryService gameQueryService;

    @Test
    @DisplayName("필터 없이 경기 목록을 조회하면 200 OK를 반환한다")
    void should_returnGameList_when_noFilter() throws Exception {
        Page<GameListResponse> page = new PageImpl<>(List.of());

        when(gameQueryService.getGames(any(), any()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/games")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("경기 목록 조회 성공"));
    }

    @Test
    @DisplayName("예매 상태로 경기 목록을 필터링할 수 있다")
    void should_filterGames_when_bookingStatusGiven() throws Exception {
        Page<GameListResponse> page = new PageImpl<>(List.of());

        when(gameQueryService.getGames(any(), any()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/games")
                .param("bookingStatus", BookingStatus.OPEN.name())
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("경기 목록 조회 성공"));
    }

    @Test
    @DisplayName("날짜 범위로 경기 목록을 필터링할 수 있다")
    void should_filterGames_when_dateRangeGiven() throws Exception {
        Page<GameListResponse> page = new PageImpl<>(List.of());

        when(gameQueryService.getGames(any(), any()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/games")
                .param("from", "2026-07-01")
                .param("to", "2026-07-31")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("경기 목록 조회 성공"));
    }

    @Test
    @DisplayName("존재하지 않는 경기 상세 조회 시 404 응답을 반환한다")
    void should_return404_when_gameNotFound() throws Exception {
        Long gameId = 999999L;

        when(gameQueryService.getGame(gameId))
            .thenThrow(new GameNotFoundException(gameId));

        mockMvc.perform(get("/api/v1/games/{gameId}", gameId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("검색 시작일이 종료일보다 늦으면 400 응답을 반환한다")
    void should_return400_when_fromIsAfterTo() throws Exception {
        when(gameQueryService.getGames(any(), any()))
            .thenThrow(new InvalidGameSearchConditionException("검색 시작일(from)은 종료일(to)보다 늦을 수 없습니다."));

        mockMvc.perform(get("/api/v1/games")
                .param("from", "2026-08-01")
                .param("to", "2026-07-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
