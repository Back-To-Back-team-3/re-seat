package com.backtoback.reseat.domain.user.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserLoginResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String userRole;
    private final Long expiresIn;

    public static UserLoginResponse of(String accessToken, String refreshToken, String userRole, long expiresIn) {
        return new UserLoginResponse(accessToken, refreshToken, userRole, expiresIn);
    }
}
