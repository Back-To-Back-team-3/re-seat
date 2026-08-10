package com.backtoback.reseat.domain.ticket.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminTicketCancelRequest(
    @NotBlank(message = "취소 상세 사유는 필수 입력 항목입니다.")
    String reason) {
}
