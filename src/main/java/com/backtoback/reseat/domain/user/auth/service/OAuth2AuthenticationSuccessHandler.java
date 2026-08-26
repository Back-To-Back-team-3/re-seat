package com.backtoback.reseat.domain.user.auth.service;

import java.io.IOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.backtoback.reseat.domain.user.auth.dto.CustomOAuth2User;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.RefreshTokenRepository;
import com.backtoback.reseat.global.security.JwtTokenProvider;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final String redirectUrl;

    public OAuth2AuthenticationSuccessHandler(
        JwtTokenProvider jwtTokenProvider,
        RefreshTokenRepository refreshTokenRepository,
        @Value("${oauth2.redirect.frontend-url:http://localhost:5173}") String redirectUrl
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.redirectUrl = redirectUrl;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    )
        throws IOException,
        ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User)authentication.getPrincipal();
        User user = oAuth2User.getUser();

        // Access Token & Refresh Token 생성
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        // Refresh Token Redis 저장 (14일 TTL 설정)
        refreshTokenRepository.save(user.getId(), refreshToken, Duration.ofDays(14));

        // 프론트엔드 리다이렉트 URL 생성 (토큰 전달)
        String targetUrl
            = UriComponentsBuilder
                .fromUriString(redirectUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                // 본인인증 여부에 따라 카카오 로그인 후 포트원 본인인증으로 넘어갈지 말지
                .queryParam("isVerified", user.isVerified())
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
