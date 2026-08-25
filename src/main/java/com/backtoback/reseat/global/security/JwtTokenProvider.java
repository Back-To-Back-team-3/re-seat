package com.backtoback.reseat.global.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    // AccessToken 만료시간 1시간(3600초)
    private final long accessTokenValidityInMilliseconds = 3600 * 1000L;
    // RefreshToken 만료시간 14일
    private final long refreshTokenValidityInMilliseconds = 14 * 24 * 60 * 60 * 1000L;
    @Value("${jwt.secret}")
    private String secretKeyString;
    private SecretKey secretKey;
    private JwtParser jwtParser;

    // 객체 생성 후 주입받은 secretKey 문자열을 암호화 키 객체로 변환
    @PostConstruct
    protected void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKeyString);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parser().verifyWith(this.secretKey).build();
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
        Date validity = new Date(now.getTime() + refreshTokenValidityInMilliseconds);

        return Jwts.builder().claims(claims).issuedAt(now).expiration(validity).signWith(secretKey).compact();
    }

    public Long getUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public Long getUserId(String token) {
        return getUserId(parseClaims(token));
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // JWT Claims 정보를 기반으로 Authentication 생성 (DB 조회 제거)
    public Authentication getAuthentication(String token) {
        return getAuthentication(parseClaims(token), token);
    }

    public boolean validateToken(String token) {
        return getClaimsIfValid(token) != null;
    }

    // 캐싱된 Parser로 Claims 파싱
    public Claims parseClaims(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    // Claims 파싱과 검증 동시 처리 Claims 객체 반환 실패 시 null
    public Claims getClaimsIfValid(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = parseClaims(token);
            if (!claims.getExpiration().before(new Date())) {
                return claims;
            }
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("[보안 이벤트] 유효하지 않은 JWT 서명 또는 잘못된 토큰 형식입니다. reason={}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.info("[보안 이벤트] 만료된 JWT 토큰입니다. reason={}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("[보안 이벤트] 지원되지 않는 JWT 토큰입니다. reason={}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("[보안 이벤트] JWT 클레임이 비어있거나 잘못되었습니다. reason={}", e.getMessage());
        } catch (Exception e) {
            log.error("[보안 이벤트] JWT 검증 중 예기치 못한 오류가 발생했습니다.", e);
        }
        return null;
    }

    // Claims로 부터 Authentication 생성 (재파싱 방지)
    public Authentication getAuthentication(Claims claims, String token) {
        String email = claims.getSubject();
        Object userIdObj = claims.get("userId");
        Long userId = (userIdObj instanceof Number number) ? number.longValue() : null;
        String userRole = claims.get("userRole", String.class);

        CustomUserDetails userDetails = CustomUserDetails.of(userId, email, userRole);
        return new UsernamePasswordAuthenticationToken(userDetails, token, userDetails.getAuthorities());
    }
}
