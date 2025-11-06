package com.leets.commitatobe.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainerConfig {

	private static final String REDIS_IMAGE = "redis:8.0.4";
	private static final String MYSQL_IMAGE = "mysql:8.0.33";
	private static final String ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:8.9.0";
	private static final GenericContainer<?> redisContainer;
	private static final MySQLContainer<?> mysqlContainer;
	private static final ElasticsearchContainer elasticsearchContainer;

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

		// Elasticsearch 컨테이너 초기화
		elasticsearchContainer = new ElasticsearchContainer(DockerImageName.parse(ELASTICSEARCH_IMAGE))
			.withEnv("discovery.type", "single-node")
			.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
			.withEnv("xpack.security.enabled", "false")
			.withEnv("xpack.monitoring.collection.enabled", "false")
			.withReuse(true);
		elasticsearchContainer.start();
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
	@ServiceConnection(name = "mysql")
	public MySQLContainer<?> mysqlContainer() {
		return mysqlContainer;
	}

	@Bean
	@ServiceConnection(name = "elasticsearch")
	public ElasticsearchContainer elasticsearchContainer() {
		return elasticsearchContainer;
	}
}
