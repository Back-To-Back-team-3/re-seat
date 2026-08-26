package com.backtoback.reseat.domain.user.verification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerificationRequest {

    @NotBlank(message = "impUid는 필수 입력값입니다.")
    private String impUid;

    public VerificationRequest(String impUid) {
        this.impUid = impUid;
    }
}
