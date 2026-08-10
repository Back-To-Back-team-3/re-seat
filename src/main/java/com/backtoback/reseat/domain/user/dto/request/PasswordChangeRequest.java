package com.backtoback.reseat.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasswordChangeRequest {

	@NotBlank(message = "현재 비밀번호는 필수 입력값입니다,")
	private String currentPassword;

	@NotBlank(message = "새로운 비밀번호는 필수 입력값입니다.")
	@Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해 주세요.")
	private String newPassword;

}
