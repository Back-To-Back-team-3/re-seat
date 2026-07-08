package com.backtoback.reseat.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasswordChangeRequest {
    @NotBlank(message = "현재 비밀번호는 필수 입력값입니다.")
    private String currentPassword;

    @NotBlank(message = "새로운 비밀번호는 필수 입력값입니다.")
    private String newPassword;
}
