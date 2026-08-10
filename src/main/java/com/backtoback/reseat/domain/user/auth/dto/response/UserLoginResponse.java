package com.backtoback.reseat.domain.user.auth.dto.response;

public record UserLoginResponse(String accessToken, String refreshToken, String userRole, Long expiresIn) {

    public static UserLoginResponse of(String accessToken, String refreshToken, String userRole, long expiresIn) {
        return new UserLoginResponse(accessToken, refreshToken, userRole, expiresIn);
    }
}
