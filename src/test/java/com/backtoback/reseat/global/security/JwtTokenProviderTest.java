package com.backtoback.reseat.global.security;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class JwtTokenProviderTest {

    private final String secretKeyString
        = Base64
            .getEncoder()
            .encodeToString("test-jwt-secret-key-must-be-at-least-256-bits-long-secure-random".getBytes());
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();

        Field secretKeyField = JwtTokenProvider.class.getDeclaredField("secretKeyString");
        secretKeyField.setAccessible(true);
        secretKeyField.set(jwtTokenProvider, secretKeyString);

        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("유효한 토큰 생성 및 Claims 기반 Authentication 생성 검증")
    void createAndGetAuthentication_Success() {
        // given
        Long userId = 1L;
        String email = "user@test.com";
        String role = "USER";

        String token = jwtTokenProvider.createAccessToken(userId, email, role);

        // when
        boolean isValid = jwtTokenProvider.validateToken(token);
        Authentication authentication = jwtTokenProvider.getAuthentication(token);

        // then
        assertThat(isValid).isTrue();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(CustomUserDetails.class);

        CustomUserDetails userDetails = (CustomUserDetails)authentication.getPrincipal();
        assertThat(userDetails.getId()).isEqualTo(userId);
        assertThat(userDetails.getUsername()).isEqualTo(email);
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("getClaimsIfValid: 유효한 토큰 전달 시 Claims를 반환하고, Claims로 Authentication 및 userId를 추출한다")
    void getClaimsIfValid_ValidToken_Success() {
        // given
        Long userId = 1L;
        String email = "user@test.com";
        String role = "USER";

        String accessToken = jwtTokenProvider.createAccessToken(userId, email, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // when
        io.jsonwebtoken.Claims accessClaims = jwtTokenProvider.getClaimsIfValid(accessToken);
        io.jsonwebtoken.Claims refreshClaims = jwtTokenProvider.getClaimsIfValid(refreshToken);
        Authentication authentication = jwtTokenProvider.getAuthentication(accessClaims, accessToken);

        // then
        assertThat(accessClaims).isNotNull();
        assertThat(jwtTokenProvider.getUserId(accessClaims)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getUserId(accessToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getEmail(accessToken)).isEqualTo(email);

        assertThat(refreshClaims).isNotNull();
        assertThat(jwtTokenProvider.getUserId(refreshClaims)).isEqualTo(userId);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        CustomUserDetails userDetails = (CustomUserDetails)authentication.getPrincipal();
        assertThat(userDetails.getId()).isEqualTo(userId);
        assertThat(userDetails.getUsername()).isEqualTo(email);
    }

    @Test
    @DisplayName("getClaimsIfValid: 유효하지 않거나 빈 토큰 전달 시 null을 반환한다")
    void getClaimsIfValid_InvalidToken_ReturnsNull() {
        // when & then
        assertThat(jwtTokenProvider.getClaimsIfValid(null)).isNull();
        assertThat(jwtTokenProvider.getClaimsIfValid("")).isNull();
        assertThat(jwtTokenProvider.getClaimsIfValid("invalid.jwt.token")).isNull();
    }

    @Test
    @DisplayName("잘못된 형식 또는 변조된 토큰 검증 시 false를 반환하고 로그를 남긴다")
    void validateToken_MalformedToken_ReturnsFalse() {
        // given
        String invalidToken = "invalid.malformed.token";

        // when
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("null 또는 빈 문자열 토큰 검증 시 false를 반환한다")
    void validateToken_EmptyToken_ReturnsFalse() {
        // when & then
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }
}
