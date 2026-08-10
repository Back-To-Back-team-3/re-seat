package com.backtoback.reseat.domain.user.admin.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.backtoback.reseat.domain.user.admin.dto.request.AdminUserRoleUpdateRequest;
import com.backtoback.reseat.domain.user.admin.dto.request.AdminUserStatusUpdateRequest;
import com.backtoback.reseat.domain.user.admin.dto.response.AdminUserResponse;
import com.backtoback.reseat.domain.user.admin.service.AdminUserService;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.global.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminUserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AdminUserService adminUserService;

	@Test
	@DisplayName("회원 목록을 검색하여 조회한다")
	void searchUsers() throws Exception {
		// given
		User user = User.builder()
			.id(1L)
			.email("test@test.com")
			.name("test")
			.phone("010-1234-5678")
			.role(UserRole.USER)
			.status(UserStatus.ACTIVE)
			.build();

		AdminUserResponse response = AdminUserResponse.from(user);
		Page<AdminUserResponse> page = new PageImpl<>(List.of(response));

		when(adminUserService.searchUsers(any(), any())).thenReturn(page);

		// when & then
		mockMvc.perform(get("/api/v1/admin/users")
				.param("email", "test@test.com")
				.param("page", "0")
				.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("회원 목록 조회 완료"))
			.andExpect(jsonPath("$.data.content[0].email").value("test@test.com"));
	}

	@Test
	@DisplayName("회원 상세 정보를 조회한다")
	void getUserDetail() throws Exception {
		// given
		User user = User.builder()
			.id(1L)
			.email("test@test.com")
			.name("test")
			.phone("010-1234-5678")
			.role(UserRole.USER)
			.status(UserStatus.ACTIVE)
			.build();

		AdminUserResponse response = AdminUserResponse.from(user);
		when(adminUserService.getUserDetail(1L)).thenReturn(response);

		// when & then
		mockMvc.perform(get("/api/v1/admin/users/{userId}", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("회원 상세 조회 완료"))
			.andExpect(jsonPath("$.data.email").value("test@test.com"));
	}

	@Test
	@DisplayName("회원 권한을 변경한다")
	void updateUserRole() throws Exception {
		// given
		AdminUserRoleUpdateRequest request = new AdminUserRoleUpdateRequest();
		request.setRole(UserRole.ADMIN);

		doNothing().when(adminUserService).updateUserRole(1L, UserRole.ADMIN);

		// when & then
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", 1L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("회원 권한 변경 완료"));
	}

	@Test
	@DisplayName("회원 상태를 변경한다")
	void updateUserStatus() throws Exception {
		// given
		AdminUserStatusUpdateRequest request = new AdminUserStatusUpdateRequest();
		request.setStatus(UserStatus.SUSPENDED);

		doNothing().when(adminUserService).updateUserStatus(1L, UserStatus.SUSPENDED);

		// when & then
		mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", 1L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("회원 상태 변경 완료"));
	}
}
