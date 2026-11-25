package com.platform.uop.integrations.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import com.platform.uop.IntegrationTest;

@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("e2e")
class RealAgentReconnectE2ETest extends AbstractRealAgentE2E {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:17"))
        .withDatabaseName("uop")
        .withUsername("postgres")
        .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void realAgent_reconnectsAfterDisconnect() {
        TestCollector testCollector = new TestCollector("cpu.usage");
        agentSdk = newAgent(testCollector);
        agentSdk.start();

        // Wait for initial observations.
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(
            () -> assertThat(testCollector.getObservationsSent()).isGreaterThan(0));

        int initialCount = testCollector.getObservationsSent();

        // Simulate a platform restart by closing and reopening the agent; it should
        // re-authenticate with the stored credentials and resume collecting.
        agentSdk.close();
        agentSdk = null;

        agentSdk = newAgent(testCollector);
        agentSdk.start();

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(
            () -> assertThat(testCollector.getObservationsSent()).isGreaterThan(initialCount));

        agentSdk.close();
        agentSdk = null;
    }
}