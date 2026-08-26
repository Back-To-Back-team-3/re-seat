package com.backtoback.reseat.domain.user.auth.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import com.backtoback.reseat.global.common.BaseUnitTest;

import jakarta.servlet.ServletException;

class OAuth2AuthenticationFailureHandlerTest extends BaseUnitTest {

    @Test
    @DisplayName("OAuth2 로그인 실패 시 설정된 프론트엔드 URL로 에러 메시지를 포함하여 리다이렉트한다")
    void onAuthenticationFailure_RedirectsToConfiguredFrontendUrl() throws IOException, ServletException {
        // given
        String frontendUrl = "https://re-seat.netlify.app";
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler(frontendUrl);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new BadCredentialsException("인증에 실패하였습니다.");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();
        assertThat(redirectedUrl).startsWith("https://re-seat.netlify.app");
        assertThat(redirectedUrl).contains("error=");
    }

    @Test
    @DisplayName("로컬 개발 환경 URL이 설정된 경우 localhost로 에러 메시지와 함께 리다이렉트한다")
    void onAuthenticationFailure_LocalEnvironment() throws IOException, ServletException {
        // given
        String frontendUrl = "http://localhost:5173";
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler(frontendUrl);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new BadCredentialsException("OAuth2 인증 실패");

        // when
        handler.onAuthenticationFailure(request, response, exception);

        // then
        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();
        assertThat(redirectedUrl).startsWith("http://localhost:5173");
        assertThat(redirectedUrl).contains("error=");
    }
}
