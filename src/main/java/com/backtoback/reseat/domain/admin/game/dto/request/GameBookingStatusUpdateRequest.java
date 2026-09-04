package com.backtoback.reseat.domain.admin.game.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경기 예매 상태 전이 요청 DTO
 */
@Getter
@NoArgsConstructor
@Schema(description = "경기 예매 상태 전이 요청")
public class GameBookingStatusUpdateRequest {

    @Schema(
        description = "목표 예매 상태",
        example = "OPEN",
        allowableValues = {
            "OPEN",
            "CLOSED",
            "CANCELLED"
        },
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "목표 예매 상태는 필수 입니다.")
    @Pattern(
        regexp = "OPEN|CLOSED|CANCELLED",
        message = "목표 예매 상태는 OPEN, CLOSED, CANCELLED 중 하나여야 합니다."
    )
    private String bookingStatus;

    @Schema(
        description = "전이 사유",
        example = "예매 오픈 시각 도달",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "전이 사유는 필수 입니다.")
    private String reason;

    /**
     * 컨트롤러/서비스에서 도메인 enum으로 변환할 때 사용한다.
     */
    public com.backtoback.reseat.domain.game.entity.BookingStatus toBookingStatus() {
        return com.backtoback.reseat.domain.game.entity.BookingStatus.valueOf(bookingStatus);
    }
}
