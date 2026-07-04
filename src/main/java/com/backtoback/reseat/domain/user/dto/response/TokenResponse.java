package com.backtoback.reseat.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    @Builder.Default
    private String grantType = "Bearer";

    private String accessToken;

    private String refreshToken;

}
