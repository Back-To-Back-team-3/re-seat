package com.backtoback.reseat.domain.user.admin.dto.response;

import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;

import lombok.Builder;

@Builder
public record AdminLoginResponse(
	String grantType,
	String accessToken,
	String refreshToken,
	Long userId,
	String email,
	String name,
	UserRole role) {
	public static AdminLoginResponse of(String accessToken, String refreshToken, User user) {
		return AdminLoginResponse.builder()
			.grantType("Bearer")
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.userId(user.getId())
			.email(user.getEmail())
			.name(user.getName())
			.role(user.getRole())
			.build();
	}
}
