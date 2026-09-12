package com.backtoback.reseat.domain.admin.game.dto.response;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;

/**
 * 관리자 경기 등록 응답 DTO.
 */
public record AdminGameResponse(Long gameId, BookingStatus bookingStatus) {
    public static AdminGameResponse from(Game game) {
        return new AdminGameResponse(game.getId(), game.getBookingStatus());
    }
}
