package com.platform.uop.integrations.e2e;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.ValueType;

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
class RealAgentDescriptorUpdateE2ETest extends AbstractRealAgentE2E {

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
    void realAgent_handlesDescriptorUpdate() {
        TestCollector testCollector = new TestCollector("cpu.usage");
        agentSdk = newAgent(testCollector);
        agentSdk.start();

        // Wait for initial registration and first observations.
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(
            () -> assertThat(testCollector.getObservationsSent()).isGreaterThan(0));

        // Update the monitor descriptor from the platform side (ID must stay the same).
        Monitor monitor = monitorRepository.findById(monitorId).orElseThrow();
        MonitorDescriptor updatedDescriptor = MonitorDescriptor.newBuilder()
            .setId(monitor.getDescriptor().getId())
            .setName("Updated Monitor")
            .setDescription("Updated via platform")
            .setVersion("2.0")
            .setActive(true)
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("cpu.usage")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build())
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("memory.usage")
                .setValueType(ValueType.VALUE_TYPE_INT64)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .build();

        monitor.updateDescriptor(updatedDescriptor);
        monitorRepository.save(monitor);

        // Push the new descriptor to the connected agent over gRPC.
        agentConnectionService.sendDescriptor(monitorId, updatedDescriptor);

        // The agent should keep running and sending observations after the update.
        int countAfterUpdate = testCollector.getObservationsSent();
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(
            () -> assertThat(testCollector.getObservationsSent()).isGreaterThan(countAfterUpdate));

        agentSdk.close();
        agentSdk = null;
    }
}