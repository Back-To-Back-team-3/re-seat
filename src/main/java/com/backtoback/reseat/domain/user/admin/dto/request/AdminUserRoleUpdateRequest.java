package com.backtoback.reseat.domain.user.admin.dto.request;

import com.backtoback.reseat.domain.user.entity.UserRole;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserRoleUpdateRequest {

	@NotNull(message = "권한은 필수값입니다.")
	private UserRole role;
}
