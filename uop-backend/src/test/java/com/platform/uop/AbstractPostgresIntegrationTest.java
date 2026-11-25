package com.platform.uop;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for DB-backed integration tests. Declares a single shared
 * Testcontainers PostgreSQL instance and wires the datasource properties, so
 * that (with a reused Surefire JVM) the database is booted and Flyway-migrated
 * once for the whole run instead of once per test class.
 *
 * <p>The container is started in a static initializer and intentionally NOT
 * annotated with {@code @Container}: the JUnit {@code @Testcontainers}
 * extension would otherwise stop it after the first test class, leaving the
 * cached Spring context (and its Hikari pool) pointing at a dead database.
 * Owning the lifecycle here keeps the container alive for the entire reused
 * JVM; the JVM exit (or Ryuk) cleans it up.
 *
 * <p>Subclasses should be annotated with {@code @IntegrationTest} (which brings
 * {@code @SpringBootTest} and the {@code test} profile) and simply extend this
 * class without declaring their own container.
 */
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
            .withDatabaseName("uop")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}