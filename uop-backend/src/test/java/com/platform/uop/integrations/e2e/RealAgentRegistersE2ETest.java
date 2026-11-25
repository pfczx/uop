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
class RealAgentRegistersE2ETest extends AbstractRealAgentE2E {

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
    void realAgent_registersAndSendsObservations() {
        TestCollector testCollector = new TestCollector("cpu.usage");
        agentSdk = newAgent(testCollector);
        agentSdk.start();

        // Wait for registration and first observations.
        await().atMost(120, TimeUnit.SECONDS).untilAsserted(
            () -> assertThat(testCollector.getObservationsSent()).isGreaterThan(0));

        // Wait for observations to be persisted.
        await().atMost(120, TimeUnit.SECONDS).untilAsserted(() -> {
            var observations = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(
                monitorId, org.springframework.data.domain.PageRequest.of(0, 100));
            assertThat(observations.getTotalElements()).isGreaterThan(0);
        });

        var savedObservations = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(
            monitorId, org.springframework.data.domain.PageRequest.of(0, 100));

        boolean hasCpuMetric = savedObservations.getContent().stream()
            .anyMatch(o -> "cpu.usage".equals(o.getObservation().getMetric())
                && o.getObservation().getValue() != null);
        assertThat(hasCpuMetric).isTrue();

        agentSdk.close();
        agentSdk = null;
    }
}