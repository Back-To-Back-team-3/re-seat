package com.backtoback.reseat.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserSignUpRequest {
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String password;

    @NotBlank(message = "이름은 필수 입력값입니다.")
    private String name;

    private String nickname;

    @NotBlank(message = "전화번호는 필수 입력값입니다.")
    @Pattern(
        regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$",
        message = "올바른 전화번호 형식(예: 010-1234-5678)이어야 합니다."
    )
    private String phone;
}
