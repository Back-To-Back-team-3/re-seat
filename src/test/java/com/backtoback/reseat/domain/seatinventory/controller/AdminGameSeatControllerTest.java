package com.backtoback.reseat.domain.seatinventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * 재고 오픈 API 통합 및 인가 테스트.
 */
@Disabled("테스트제외")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameSeatControllerTest {

    private static final long SEEDED_STADIUM_ID = 1L;
    private static final long NOT_EXISTING_GAME_ID = 999_999L;
    private static final String OPEN_INVENTORY_URI = "/api/v1/admin/games/{gameId}/seats";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private Long gameIdWithSeats;

    @BeforeEach
    void setUp() {
        gameIdWithSeats
            = entityManager
                .createQuery("select g.id from Game g where g.stadium.id = :stadiumId order by g.id asc", Long.class)
                .setParameter("stadiumId", SEEDED_STADIUM_ID)
                .setMaxResults(1)
                .getSingleResult();
    }

    @DisplayName("ADMIN이 재고 오픈을 요청하면 201 Created와 생성 결과를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return201_when_adminOpensInventory() throws Exception {
        mockMvc
            .perform(post(OPEN_INVENTORY_URI, gameIdWithSeats))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.gameId").value(gameIdWithSeats))
            .andExpect(jsonPath("$.data.createdCount").value(500))
            .andExpect(jsonPath("$.data.priceRange.min").isNumber())
            .andExpect(jsonPath("$.data.priceRange.max").isNumber());
    }

    @DisplayName("미인증 사용자는 401을 받는다")
    @WithAnonymousUser
    @Test
    void should_return401_when_anonymousUser() throws Exception {
        mockMvc.perform(post(OPEN_INVENTORY_URI, gameIdWithSeats)).andDo(print()).andExpect(status().isUnauthorized());
    }

    @DisplayName("ADMIN이 아닌 일반 사용자는 403을 받는다")
    @WithMockUser(roles = "USER")
    @Test
    void should_return403_when_normalUser() throws Exception {
        mockMvc.perform(post(OPEN_INVENTORY_URI, gameIdWithSeats)).andDo(print()).andExpect(status().isForbidden());
    }

    @DisplayName("존재하지 않는 경기는 404 GAME_NOT_FOUND를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_gameNotFound() throws Exception {
        mockMvc
            .perform(post(OPEN_INVENTORY_URI, NOT_EXISTING_GAME_ID))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("이미 오픈된 경기를 재호출하면 409 SEAT_INVENTORY_ALREADY_OPENED를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return409_when_calledTwice() throws Exception {
        // given
        mockMvc.perform(post(OPEN_INVENTORY_URI, gameIdWithSeats)).andExpect(status().isCreated());

        // when & then
        mockMvc
            .perform(post(OPEN_INVENTORY_URI, gameIdWithSeats))
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_ALREADY_OPENED"));
    }
}
