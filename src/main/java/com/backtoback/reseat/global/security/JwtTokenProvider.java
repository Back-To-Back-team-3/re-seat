package com.backtoback.reseat.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKeyString;

    private SecretKey secretKey;

    // AccessToken 만료시간 1시간(3600초)
    private final long accessTokenValidityInMilliseconds = 3600 * 1000L;

    // RefreshToken 만료시간 14일
    private final Long refreshToeknValidityInMillseconds = 14 * 24 * 60 * 60 * 1000L;

    // 객체 생성 후 주입받은 secretKey 문자열을 암호화 키 객체로 변환
    @PostConstruct
    protected void init(){
        byte[] keyBytes = Decoders.BASE64.decode(secretKeyString);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // access Token 생성
    public String createAccessToken(Long userId, String email, String userRole){
        Claims claims = Jwts.claims()
                .subject(email)
                .add("userId", userId)
                .add("userRole", userRole)
                .build();

        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityInMilliseconds);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }

    // refresh Token 생성
    public String createRefreshToken(Long userId){
        Claims claims = Jwts.claims()
                .add("userId", userId)
                .build();

        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshToeknValidityInMillseconds);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return claims.get("userId", Long.class);
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public org.springframework.security.core.Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        // 토큰 내부 클레임에서 권한 추출 (USER, ADMIN 등)
        String role = claims.get("userRole", String.class);
        List<GrantedAuthority> authorities =
                java.util.Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));

        // 시큐리티 전용 User 객체 생성 (이메일, 패스워드더미, 권한)
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(claims.getSubject(), "", authorities);

        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, token, authorities);
    }


    public boolean validateToken(String token) {
        try {
            return !parseClaims(token)
                    .getExpiration()
                    .before(new Date());
        } catch (Exception e) {
            return false;
        }
    }


    private io.jsonwebtoken.Claims parseClaims(String token) {
        return io.jsonwebtoken.Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}