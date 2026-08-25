package com.backtoback.reseat.domain.ticket.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 티켓 강제취소 요청")
public record AdminTicketCancelRequest(
    @Schema(
        description = "취소 상세 사유",
        example = "매크로 부정 예매 탐지",
        requiredMode = Schema.RequiredMode.REQUIRED
    ) @NotBlank(message = "취소 상세 사유는 필수 입력 항목입니다.") String reason
) {
}
