package com.backtoback.reseat.domain.user.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import com.backtoback.reseat.domain.user.auth.dto.CustomOAuth2User;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.global.common.BaseUnitTest;
import com.backtoback.reseat.global.security.JwtTokenProvider;

import jakarta.servlet.ServletException;

class OAuth2AuthenticationSuccessHandlerTest extends BaseUnitTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private Authentication authentication;

    @Test
    @DisplayName("OAuth2 로그인 성공 시 설정된 프론트엔드 URL로 토큰 및 인증 정보를 포함하여 리다이렉트한다")
    void onAuthenticationSuccess_RedirectsToConfiguredFrontendUrl() throws IOException, ServletException {
        // given
        String frontendUrl = "https://re-seat.netlify.app";
        OAuth2AuthenticationSuccessHandler handler
            = new OAuth2AuthenticationSuccessHandler(jwtTokenProvider, refreshTokenRepository, frontendUrl);

        User user = User.builder()
            .id(1L)
            .email("test@kakao.com")
            .name("홍길동")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .isVerified(true)
            .build();

        CustomOAuth2User oAuth2User = new CustomOAuth2User(user, Map.of("id", "12345"));
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(jwtTokenProvider.createAccessToken(1L, "test@kakao.com", "USER")).thenReturn("mock-access-token");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("mock-refresh-token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();
        assertThat(redirectedUrl).startsWith("https://re-seat.netlify.app");
        assertThat(redirectedUrl).contains("accessToken=mock-access-token");
        assertThat(redirectedUrl).contains("refreshToken=mock-refresh-token");
        assertThat(redirectedUrl).contains("isVerified=true");

        verify(refreshTokenRepository, times(1)).save(1L, "mock-refresh-token", Duration.ofDays(14));
    }

    @Test
    @DisplayName("로컬 개발 환경 URL이 설정된 경우 localhost로 정상 리다이렉트한다")
    void onAuthenticationSuccess_LocalEnvironment() throws IOException, ServletException {
        // given
        String frontendUrl = "http://localhost:5173";
        OAuth2AuthenticationSuccessHandler handler
            = new OAuth2AuthenticationSuccessHandler(jwtTokenProvider, refreshTokenRepository, frontendUrl);

        User user = User.builder()
            .id(2L)
            .email("local@kakao.com")
            .name("로컬유저")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .isVerified(false)
            .build();

        CustomOAuth2User oAuth2User = new CustomOAuth2User(user, Map.of("id", "67890"));
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(jwtTokenProvider.createAccessToken(2L, "local@kakao.com", "USER")).thenReturn("local-access-token");
        when(jwtTokenProvider.createRefreshToken(2L)).thenReturn("local-refresh-token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();
        assertThat(redirectedUrl).startsWith("http://localhost:5173");
        assertThat(redirectedUrl).contains("accessToken=local-access-token");
        assertThat(redirectedUrl).contains("refreshToken=local-refresh-token");
        assertThat(redirectedUrl).contains("isVerified=false");
    }
}
