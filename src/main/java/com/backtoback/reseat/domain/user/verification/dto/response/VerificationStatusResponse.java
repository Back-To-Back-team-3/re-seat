package com.backtoback.reseat.domain.user.verification.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VerificationStatusResponse {

    private final boolean isVerified;

    public static VerificationStatusResponse from(boolean isVerified) {
        return new VerificationStatusResponse(isVerified);
    }
}
