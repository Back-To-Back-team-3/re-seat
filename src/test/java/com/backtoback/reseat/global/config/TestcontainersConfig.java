package com.backtoback.reseat.global.config;

//Testcontainers 기반 독립 테스트 환경
//MySQL/PostgreSQL, Redis, Kafka 컨테이너 자동 구동 및 Spring DynamicPropertyRegistry 연동

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestcontainersConfig {

	//MySQL 컨테이너 선언
	private static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0")
		.withDatabaseName("reseat_test")
		.withUsername("test")
		.withPassword("test");

	// Redis 컨테이너 선언
	private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
		DockerImageName.parse("redis:7.0-alpine"))
		.withExposedPorts(6379);

	static {
		MYSQL_CONTAINER.start();
		REDIS_CONTAINER.start();
	}

	//Spring Boot 프로퍼티에 동적으로 컨테이너 접속 정보 주입
	@Bean
	public DynamicPropertyRegistrar overrideProps() {
		return registry -> {
			// MySQL 설정 주입
			registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
			registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);
			registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
			registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
			registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");

			// Redis 설정 주입
			registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
			registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
		};
	}
}
