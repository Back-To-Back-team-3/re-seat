package com.backtoback.reseat.domain.admin.game.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.backtoback.reseat.domain.admin.game.service.AdminGameBookingService;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.exception.InvalidBookingStatusTransitionException;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryNotOpenedException;

/**
 * 경기 예매 상태 전이 API 인가·성공·실패 테스트.
 * <p>웹 계층만 슬라이스로 띄우는 @WebMvcTest로 작성했다.
 * <p>서비스 내부 로직(상태 전이 규칙, CAS 원자성)은 GameTest·도메인 테스트가 별도로 검증하며,
 * 이 클래스는 컨트롤러가 요청을 받아 서비스를 올바르게 호출하고 상태코드를 올바르게 반환하는지만 검증한다.
 */
@WebMvcTest(AdminGameBookingController.class)
class AdminGameBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminGameBookingService adminGameBookingService;

    /**
     * 요청 바디 JSON을 매번 손으로 이어붙이지 않도록 헬퍼로 뺐다.
     */
    private String body(String bookingStatus, String reason) {
        return String.format("{\"bookingStatus\":\"%s\",\"reason\":\"%s\"}", bookingStatus, reason);
    }

    @Test
    @DisplayName("ADMIN 권한으로 요청하면 200을 반환한다")
    @WithMockUser(roles = "ADMIN")
    void updateBookingStatus_returns200_whenAdmin() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/admin/games/{gameId}/booking-status", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "예매 오픈 시각 도달"))
            )
            .andExpect(status().isOk());

        // 컨트롤러가 요청 바디를 그대로 흘려보내는지(변형·누락 없이) 확인한다.
        verify(adminGameBookingService).transition(1L, BookingStatus.OPEN, "예매 오픈 시각 도달");
    }

    @Test
    @DisplayName("미인증 사용자는 401을 받는다")
    @WithAnonymousUser
    void updateBookingStatus_returns401_whenAnonymous() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/admin/games/{gameId}/booking-status", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자 권한으로 요청 시 403을 반환한다")
    @WithMockUser(roles = "USER")
    void updateBookingStatus_returns403_whenNotAdmin() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/admin/games/{gameId}/booking-status", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 경기 요청 시 404를 반환한다")
    @WithMockUser(roles = "ADMIN")
    void updateBookingStatus_returns404_whenGameNotFound() throws Exception {
        // 서비스가 GameNotFoundException을 던지는 상황을 흉내내 컨트롤러의 예외 전파만 검증한다.
        // 실제 조회 실패 조건(존재하지 않는 gameId)은 서비스 계층 책임이라 여기서는 모킹으로 대체한다.
        doThrow(new GameNotFoundException(999L)).when(adminGameBookingService).transition(eq(999L), any(), any());

        mockMvc
            .perform(
                patch("/api/v1/admin/games/{gameId}/booking-status", 999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("bookingStatus 값이 허용되지 않으면 400을 반환한다")
    @WithMockUser(roles = "ADMIN")
    void updateBookingStatus_returns400_whenBookingStatusInvalid() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/admin/games/{gameId}/booking-status", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SUSPENDED", "사유"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        // @Pattern 검증이 서비스 호출 전(Bean Validation 단계)에 걸러내는지 확인한다.
        verifyNoInteractions(adminGameBookingService);
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이 요청 시 409를 반환한다")
    @WithMockUser(roles = "ADMIN")
    void updateBookingStatus_returns409_whenInvalidTransition() throws Exception {
        doThrow(new InvalidBookingStatusTransitionException(BookingStatus.CLOSED, BookingStatus.OPEN))
            .when(adminGameBookingService)
            .transition(eq(1L), any(), any());

        mockMvc
            .perform(
                patch("/api/v1/admin/games/{gameId}/booking-status", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("INVALID_BOOKING_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("좌석 재고 미오픈 상태에서 OPEN 요청 시 409를 반환한다")
    @WithMockUser(roles = "ADMIN")
    void updateBookingStatus_returns409_whenSeatInventoryNotOpened() throws Exception {
        doThrow(new SeatInventoryNotOpenedException(1L)).when(adminGameBookingService).transition(eq(1L), any(), any());

        mockMvc
            .perform(
                patch("/api/v1/admin/games/{gameId}/booking-status", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_NOT_OPENED"));
    }
}
