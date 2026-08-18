package com.backtoback.reseat.global.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.verification.service.CustomUserDetailsService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    // 커스텀 유저 디테일 서비스를 주입받습니다.
    private final CustomUserDetailsService customUserDetailsService;
    // AccessToken 만료시간 1시간(3600초)
    private final long accessTokenValidityInMilliseconds = 3600 * 1000L;
    // RefreshToken 만료시간 14일
    private final Long refreshToeknValidityInMillseconds = 14 * 24 * 60 * 60 * 1000L;
    @Value("${jwt.secret}")
    private String secretKeyString;
    private SecretKey secretKey;

    // 객체 생성 후 주입받은 secretKey 문자열을 암호화 키 객체로 변환
    @PostConstruct
    protected void init() {
        if (secretKeyString == null || secretKeyString.isBlank()) {
            throw new IllegalStateException("JWT secret key가 설정되지 않았습니다. (JWT_SECRET 환경변수를 확인하세요)");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKeyString);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT secret key가 올바른 Base64 형식이 아닙니다.", e);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                String.format("JWT secret key는 최소 32바이트(256비트) 이상이어야 합니다. (현재 디코딩된 길이: %d 바이트)", keyBytes.length)
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // access Token 생성
    public String createAccessToken(Long userId, String email, String userRole) {
        Claims claims = Jwts.claims().subject(email).add("userId", userId).add("userRole", userRole).build();

        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityInMilliseconds);

        return Jwts.builder().claims(claims).issuedAt(now).expiration(validity).signWith(secretKey).compact();
    }

    // refresh Token 생성
    public String createRefreshToken(Long userId) {
        Claims claims = Jwts.claims().add("userId", userId).build();

        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshToeknValidityInMillseconds);

        return Jwts.builder().claims(claims).issuedAt(now).expiration(validity).signWith(secretKey).compact();
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return claims.get("userId", Long.class);
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // Principal 타입을 CustomUserDetails로 일치화
    public org.springframework.security.core.Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String email = claims.getSubject();

        // 기존의 뉴비 껍데기 시큐리티 User 생성 코드를 제거하고,
        // 진짜 DB 유저 데이터를 물고 있는 CustomUserDetails를 로드
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userDetails,
            token,
            userDetails.getAuthorities()
        );
    }

    public boolean validateToken(String token) {
        try {
            return !parseClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private io.jsonwebtoken.Claims parseClaims(String token) {
        return io.jsonwebtoken.Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}
