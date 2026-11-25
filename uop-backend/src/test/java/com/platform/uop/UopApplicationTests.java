package com.platform.uop;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.platform.uop.IntegrationTest;

/**
 * Smoke test that verifies the full Spring application context loads with all
 * beans wired correctly. Uses the {@link IntegrationTest} meta-annotation plus a
 * Testcontainers PostgreSQL for the datasource.
 */
@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
class UopApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies the application context starts and all beans are wired.
    }
}