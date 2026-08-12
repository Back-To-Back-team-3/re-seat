package com.backtoback.reseat.domain.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TicketVerifyRequest {

    @NotBlank
    private String qrToken;

    @NotNull
    private Long gameId;
}
