package com.backtoback.reseat.domain.user.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.user.admin.dto.request.AdminUserRoleUpdateRequest;
import com.backtoback.reseat.domain.user.admin.dto.request.AdminUserStatusUpdateRequest;
import com.backtoback.reseat.domain.user.admin.dto.request.UserSearchCondition;
import com.backtoback.reseat.domain.user.admin.dto.response.AdminUserResponse;
import com.backtoback.reseat.domain.user.admin.service.AdminUserService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

	private final AdminUserService adminUserService;

	@GetMapping
	//반환 타입을 PageResponse<AdminUserResponse> 로 격리 수용 변경
	public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> searchUsers(
		UserSearchCondition condition,
		@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
		Pageable pageable) {

		Page<AdminUserResponse> pageResult = adminUserService.searchUsers(condition, pageable);

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponse.success("회원 목록 조회 완료", PageResponse.of(pageResult)));
	}

	@GetMapping("/{userId}")
	public ResponseEntity<ApiResponse<AdminUserResponse>> getUserDetail(@PathVariable
	Long userId) {
		AdminUserResponse response = adminUserService.getUserDetail(userId);
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponse.success("회원 상세 조회 완료", response));
	}

	@PatchMapping("/{userId}/role")
	public ResponseEntity<ApiResponse<Void>> updateUserRole(
		@PathVariable
		Long userId,
		@Valid @RequestBody
		AdminUserRoleUpdateRequest request) {

		adminUserService.updateUserRole(userId, request.getRole());
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponse.success("회원 권한 변경 완료", null));
	}

	@PatchMapping("/{userId}/status")
	public ResponseEntity<ApiResponse<Void>> updateUserStatus(
		@PathVariable
		Long userId,
		@Valid @RequestBody
		AdminUserStatusUpdateRequest request) {

		adminUserService.updateUserStatus(userId, request.getStatus());
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(ApiResponse.success("회원 상태 변경 완료", null));
	}
}
