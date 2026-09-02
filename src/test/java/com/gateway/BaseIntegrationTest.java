package com.gateway;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class BaseIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES;
    private static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

        // Start both containers exactly once, shared across all tests and JVMs.
        // Do NOT use @Container here: @Container re-runs JUnit lifecycle per Spring
        // context, causing port collisions and Postgres crashes when multiple
        // @SpringBootTest context caches (MOCK vs RANDOM_PORT) are active in a suite.
        POSTGRES.start();
        REDIS.start();

        // Ensure containers are cleaned up when the JVM exits.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            POSTGRES.stop();
            REDIS.stop();
        }));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}