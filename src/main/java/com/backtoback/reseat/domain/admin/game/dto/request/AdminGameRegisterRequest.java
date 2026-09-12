package com.backtoback.reseat.domain.admin.game.dto.request;

import java.time.LocalDateTime;

import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 경기 등록 요청 DTO.
 */
public record AdminGameRegisterRequest(
    @NotNull Long stadiumId,
    @NotNull Long homeTeamId,
    @NotNull Long awayTeamId,

    @NotNull @Future @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime gameAt,

    @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime bookingOpenAt,

    @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime bookingCloseAt,

    @Size(max = 255) String title
) {
    // 선택 적용: bookingOpenAt < bookingCloseAt <= gameAt 순서 검증.
    public AdminGameRegisterRequest {
        if (bookingOpenAt != null && bookingCloseAt != null && !bookingOpenAt.isBefore(bookingCloseAt)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "예매 오픈 시각은 예매 마감 시각보다 이전이어야 합니다.");
        }
        if (bookingCloseAt != null && gameAt != null && bookingCloseAt.isAfter(gameAt)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "예매 마감 시각은 경기 일시 이후일 수 없습니다.");
        }
    }
}
