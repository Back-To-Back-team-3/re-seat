package com.backtoback.reseat.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test | redis-test")
public class RedissonConfig {

    private static final String REDISSON_HOST_PREFIX = "redis://";
    @Value("${spring.data.redis.host:localhost}")
    private String host;
    @Value("${spring.data.redis.port:6379}")
    private int port;
    // application.yml의 패스워드 읽기
    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        var singleServerConfig = config.useSingleServer().setAddress(REDISSON_HOST_PREFIX + host + ":" + port);

        // 비밀번호가 설정되어 있다면 Redisson에 주입합니다
        if (password != null && !password.trim().isEmpty()) {
            singleServerConfig.setPassword(password.trim());
        }

        return Redisson.create(config);
    }
}
