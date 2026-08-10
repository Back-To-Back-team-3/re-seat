package com.backtoback.reseat.global.service;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.global.config.DatabaseCleaner;

//RDB 및 인메모리 DB 통합 격리 서비스
@Service
public class TestDatabaseCleanUpService {
    private final DatabaseCleaner databaseCleaner;
    private final StringRedisTemplate redisTemplate;

    public TestDatabaseCleanUpService(DatabaseCleaner databaseCleaner, StringRedisTemplate redisTemplate) {
        this.databaseCleaner = databaseCleaner;
        this.redisTemplate = redisTemplate;
    }

    //모든 저장소 RDB+Redis 초기화
    @Transactional
    public void cleanUpAll() {
        // MySQL RDB 테이블 Truncate
        databaseCleaner.execute();

        // Redis 데이터 전체 삭제 (RedisCallback으로 커넥션 안전 관리)
        redisTemplate.execute((RedisCallback<Object>)connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
