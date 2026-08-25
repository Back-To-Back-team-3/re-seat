package com.backtoback.reseat.domain.user.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "RT:";

    // RefreshToken 저장 (TTL 설정 14일)
    public void save(Long userId, String refreshToken, Duration ttl) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken, ttl);
    }

    // Refresh Token 조회
    public Optional<String> findByUserId(Long userId) {
        String key = KEY_PREFIX + userId;
        String token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token);
    }

    // Token 삭제
    public void deleteByUserId(Long userId) {
        String key = KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
