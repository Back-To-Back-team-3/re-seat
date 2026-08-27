package com.backtoback.reseat.domain.user.verification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PortoneTokenResponse {
    private int code;
    private String message;
    private TokenData response;

    @Getter
    @NoArgsConstructor
    public static class TokenData {
        @JsonProperty("access_token")
        private String accessToken;
        private long now;
        @JsonProperty("expired_at")
        private long expiredAt;
    }
}
