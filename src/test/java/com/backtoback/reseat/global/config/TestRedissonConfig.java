package com.backtoback.reseat.global.config;

import java.util.concurrent.TimeUnit;

import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test & !redis-test")
public class TestRedissonConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        RedissonClient mockClient = Mockito.mock(RedissonClient.class);
        RLock mockLock = Mockito.mock(RLock.class);
        try {
            Mockito.when(mockClient.getLock(Mockito.anyString())).thenReturn(mockLock);
            Mockito
                .when(mockLock.tryLock(Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class)))
                .thenReturn(true);
            Mockito.when(mockLock.tryLock(Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(true);
            Mockito.when(mockLock.tryLock()).thenReturn(true);
            Mockito.when(mockLock.isHeldByCurrentThread()).thenReturn(true);
            Mockito.when(mockLock.isLocked()).thenReturn(true);

        } catch (InterruptedException ignored) {}
        return mockClient;
    }
}
