package com.backtoback.reseat.domain.user.verification.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationStatusResponse {

    private final boolean isVerified;
    private final String name;
    private final String phone;

    public static VerificationStatusResponse of(boolean isVerified, String name, String phone) {
        return VerificationStatusResponse.builder().isVerified(isVerified).name(name).phone(phone).build();
    }
}
