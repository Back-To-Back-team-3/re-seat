package com.backtoback.reseat.domain.user.admin.dto.request;

import com.backtoback.reseat.domain.user.entity.UserStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserStatusUpdateRequest {

	@NotNull(message = "상태는 필수값입니다.")
	private UserStatus status;
}
