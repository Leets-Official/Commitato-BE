package com.leets.commitatobe.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerConfig {

	private static final String REDIS_IMAGE = "redis:8.0.4";
	private static final String MYSQL_IMAGE = "mysql:8.0.33";
	private static final GenericContainer<?> redisContainer;
	private static final MySQLContainer<?> mysqlContainer;

	static {

		// Redis 컨테이너 초기화
		redisContainer = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
			.withExposedPorts(6379)
			.withReuse(true);
		redisContainer.start();

		// MySQL 컨테이너 초기화
		mysqlContainer = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
			.withReuse(true);
		mysqlContainer.start();
	}

	// Testcontainers의 동적 포트를 Spring 속성에 등록
	@DynamicPropertySource
	static void registerDynamicProperties(DynamicPropertyRegistry registry) {

		// Redis 설정
		registry.add("spring.data.redis.host", redisContainer::getHost);
		registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

	}

	@Bean
	@ServiceConnection(name = "redis")
	public GenericContainer<?> redisContainer() {
		return redisContainer;
	}

	@Bean
	@ServiceConnection
	public MySQLContainer<?> mysqlContainer() {
		return mysqlContainer;
	}
}
