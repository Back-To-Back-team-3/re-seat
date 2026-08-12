package com.backtoback.reseat.domain.user.auth.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.backtoback.reseat.domain.user.auth.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.auth.service.AuthService;
import com.backtoback.reseat.global.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthService authService;

	@Test
	@DisplayName("로그인 요청 성공 시 200 OK와 토큰 정보를 반환한다")
	void login_Success() throws Exception {
		// given
		UserLoginRequest request = new UserLoginRequest("user@test.com", "Password123!");
		TokenResponse response
		    = TokenResponse.builder().accessToken("mock-access-token").refreshToken("mock-refresh-token").build();

		when(authService.login(any(UserLoginRequest.class))).thenReturn(response);

		// when & then
		mockMvc
		    .perform(
		        post("/api/v1/auth/login")
		            .contentType(MediaType.APPLICATION_JSON)
		            .content(objectMapper.writeValueAsString(request))
		    )
		    .andExpect(status().isOk())
		    .andExpect(jsonPath("$.success").value(true))
		    .andExpect(jsonPath("$.message").value("로그인 성공 완료"))
		    .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"))
		    .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"));
	}

	@Test
	@DisplayName("로그인 요청 시 필수 항목(이메일, 비밀번호) 누락 시 400 Bad Request를 반환한다")
	void login_InvalidRequest() throws Exception {
		// given
		UserLoginRequest request = new UserLoginRequest("", "");

		// when & then
		mockMvc
		    .perform(
		        post("/api/v1/auth/login")
		            .contentType(MediaType.APPLICATION_JSON)
		            .content(objectMapper.writeValueAsString(request))
		    )
		    .andExpect(status().isBadRequest());
	}
}
