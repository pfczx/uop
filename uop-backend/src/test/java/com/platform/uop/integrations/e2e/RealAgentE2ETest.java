package com.platform.uop.integrations.e2e;

import com.platform.uop.agent.sdk.AgentSdk;
import com.platform.uop.agent.sdk.Collector;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.MonitorService;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.observation.entity.ObservationBatch;
import com.platform.uop.observation.repository.ObservationBatchRepository;
import com.platform.uop.observation.repository.ObservationRepository;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;
import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;
import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;
import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.dto.LoginResponse;
import com.platform.uop.users.dto.CreateUserRequest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import org.awaitility.Awaitility;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

import com.platform.uop.IntegrationTest;

import org.junit.jupiter.api.Disabled;

@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("Requires gRPC server running - disabled in test profile")
class RealAgentE2ETest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private AgentCredentialService agentCredentialService;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private ObservationBatchRepository observationBatchRepository;

    private Path tempDir;
    private String adminSessionCookie;
    private UUID workspaceId;
    private UUID monitorId;
    private String registrationToken;
    private AgentSdk agentSdk;

    private static final String ADMIN_EMAIL = "admin@realagent.test";
    private static final String ADMIN_PASSWORD = "password123";

    @BeforeEach
    void setUp() throws Exception {
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        tempDir = Files.createTempDirectory("real-agent-e2e-test");
        Path credFile = tempDir.resolve("credentials");

        adminSessionCookie = loginAsAdmin();
        
        // Create workspace
        String workspaceName = "Real Agent E2E Workspace " + UUID.randomUUID();
        workspaceId = createWorkspace(workspaceName);

        // Create monitor
        monitorId = createMonitorViaService(workspaceId.toString(), "Real Agent Monitor");

        // Get registration token
        String installCommand = monitorService.generateInstallationCommand(workspaceId, monitorId);
        registrationToken = extractRegistrationToken(installCommand);
        
        assertThat(registrationToken).isNotNull();
        assertThat(registrationToken).isNotEmpty();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (agentSdk != null) {
            agentSdk.close();
        }
        if (tempDir != null) {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    @Transactional
    @Rollback(false)
    void realAgent_registersAndSendsObservations() throws Exception {
        // Create a test collector that generates observations
        TestCollector testCollector = new TestCollector();
        
        // Start agent with real gRPC connection
        agentSdk = AgentSdk.builder()
            .platformHost("localhost")
            .platformPort(9090)
            .registrationToken(registrationToken)
            .credentialsPath(tempDir.resolve("credentials"))
            .collector(testCollector)
            .build();
        
        agentSdk.start();

        // Wait for registration and first observations
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testCollector.getObservationsSent()).isGreaterThan(0);
        });

        // Wait for observations to be persisted
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var observations = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(monitorId, 
                org.springframework.data.domain.PageRequest.of(0, 100));
            assertThat(observations.getTotalElements()).isGreaterThanOrEqualTo(testCollector.getObservationsSent());
        });

        // Verify data integrity
        var savedObservations = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(monitorId,
            org.springframework.data.domain.PageRequest.of(0, 100));
        
        assertThat(savedObservations.getTotalElements()).isGreaterThan(0);
        
        // Verify metric names and values
        boolean hasCpuMetric = savedObservations.getContent().stream()
            .anyMatch(o -> "cpu.usage".equals(o.getObservation().getMetric()) && o.getObservation().getValue() != null);
        assertThat(hasCpuMetric).isTrue();

        // Stop agent
        agentSdk.close();
        agentSdk = null;

        // Verify agent stopped
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testCollector.isRunning()).isFalse();
        });
    }

    @Test
    @Transactional
    @Rollback(false)
    void realAgent_reconnectsAfterDisconnect() throws Exception {
        TestCollector testCollector = new TestCollector();
        
        agentSdk = AgentSdk.builder()
            .platformHost("localhost")
            .platformPort(9090)
            .registrationToken(registrationToken)
            .credentialsPath(tempDir.resolve("credentials"))
            .collector(testCollector)
            .build();
        
        agentSdk.start();

        // Wait for initial observations
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testCollector.getObservationsSent()).isGreaterThan(0);
        });

        int initialCount = testCollector.getObservationsSent();

        // Simulate platform restart by closing and reopening agent
        agentSdk.close();
        agentSdk = null;
        
        Thread.sleep(2000);

        // Create new agent with same credentials (should re-authenticate)
        agentSdk = AgentSdk.builder()
            .platformHost("localhost")
            .platformPort(9090)
            .registrationToken(registrationToken)
            .credentialsPath(tempDir.resolve("credentials"))
            .collector(testCollector)
            .build();
        
        agentSdk.start();

        // Wait for more observations after reconnect
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testCollector.getObservationsSent()).isGreaterThan(initialCount);
        });

        // Verify all observations persisted
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var observations = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(monitorId,
                org.springframework.data.domain.PageRequest.of(0, 100));
            assertThat(observations.getTotalElements()).isGreaterThanOrEqualTo(testCollector.getObservationsSent());
        });
    }

    @Test
    @Transactional
    @Rollback(false)
    void realAgent_handlesDescriptorUpdate() throws Exception {
        TestCollector testCollector = new TestCollector();
        
        agentSdk = AgentSdk.builder()
            .platformHost("localhost")
            .platformPort(9090)
            .registrationToken(registrationToken)
            .credentialsPath(tempDir.resolve("credentials"))
            .collector(testCollector)
            .build();
        
        agentSdk.start();

        // Wait for initial registration
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testCollector.getDescriptorVersion()).isEqualTo("1.0");
            assertThat(testCollector.isRegistered()).isTrue();
        });

        // Update monitor descriptor from platform
        MonitorDescriptor updatedDescriptor = MonitorDescriptor.newBuilder()
            .setId("real-agent-monitor-" + UUID.randomUUID())
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

        Monitor monitor = monitorRepository.findById(monitorId).orElseThrow();
        monitor.updateDescriptor(updatedDescriptor);
        monitorRepository.save(monitor);

        // Send descriptor update via gRPC
        // Note: In real scenario, platform would push this. Here we verify agent can handle it.
        
        // Wait for agent to receive and apply new descriptor
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testCollector.getDescriptorVersion()).isEqualTo("2.0");
        });

        // Verify agent is now collecting new metric
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean hasMemoryMetric = testCollector.getCollectedMetrics().contains("memory.usage");
            assertThat(hasMemoryMetric).isTrue();
        });
    }

    private String loginAsAdmin() {
        String adminEmail = ADMIN_EMAIL;
        String password = ADMIN_PASSWORD;
        
        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + adminEmail + "\",\"password\":\"" + password + "\"}")
            .when()
            .post("/auth/login");
        
        if (loginResponse.statusCode() == 200) {
            String cookie = loginResponse.getCookie("JSESSIONID");
            if (cookie != null) {
                return "JSESSIONID=" + cookie;
            }
        }
        
        Response setupResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + adminEmail + "\",\"password\":\"" + password + "\"}")
            .when()
            .post("/setup/admin");
        
        if (setupResponse.statusCode() != 200 && setupResponse.statusCode() != 409) {
            throw new AssertionError("Setup failed with status " + setupResponse.statusCode() + ". Body: " + setupResponse.getBody().asString());
        }

        Response response = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + adminEmail + "\",\"password\":\"" + password + "\"}")
            .when()
            .post("/auth/login");
        
        if (response.statusCode() != 200) {
            throw new AssertionError("Login failed with status " + response.statusCode() + ". Body: " + response.getBody().asString());
        }
        
        response = response.then().extract().response();
        String cookie = response.getCookie("JSESSIONID");
        if (cookie != null) {
            return "JSESSIONID=" + cookie;
        }
        throw new IllegalStateException("No JSESSIONID cookie found");
    }

    private UUID createWorkspace(String name) {
        Response response = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\",\"description\":\"Test\"}")
            .when()
            .post("/workspaces");
        
        int status = response.statusCode();
        String body = response.getBody().asString();
        
        if (status != 201) {
            throw new AssertionError("Expected 201 but got " + status + ". Body: " + body);
        }
        
        String id = response.jsonPath().getString("id");
        assertThat(id).isNotNull();
        return UUID.fromString(id);
    }

    private UUID createMonitorViaService(String workspaceId, String name) throws com.google.protobuf.InvalidProtocolBufferException {
        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("real-agent-monitor-" + UUID.randomUUID())
            .setName(name)
            .setDescription("Real Agent E2E Test Monitor")
            .setVersion("1.0")
            .setActive(true)
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("cpu.usage")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build())
            .build();
        
        CreateMonitorRequest request = new CreateMonitorRequest(descriptor);
        MonitorResponse response = monitorService.create(request, UUID.fromString(workspaceId));
        return response.id();
    }

    private String extractRegistrationToken(String installCommand) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("--registration-token\\s+(\\S+)");
        java.util.regex.Matcher matcher = pattern.matcher(installCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // Test collector that generates sample metrics
    static class TestCollector implements Collector {
        private final CopyOnWriteArrayList<String> collectedMetrics = new CopyOnWriteArrayList<>();
        private final AtomicInteger observationsSent = new AtomicInteger(0);
        private volatile String descriptorVersion = "0";
        private volatile boolean registered = false;
        private volatile boolean running = false;
        private Thread collectorThread;
        private MonitorDescriptor currentDescriptor;

        @Override
        public MetricDescriptor getDescriptor() {
            // Return first metric as descriptor (simplified)
            return currentDescriptor != null && !currentDescriptor.getMetricsList().isEmpty()
                ? currentDescriptor.getMetricsList().get(0)
                : MetricDescriptor.getDefaultInstance();
        }

        public void start(MonitorDescriptor descriptor) {
            this.currentDescriptor = descriptor;
            this.descriptorVersion = descriptor.getVersion();
            this.registered = true;
            this.running = true;
            
            // Collect metrics from descriptor
            descriptor.getMetricsList().forEach(m -> collectedMetrics.add(m.getName()));
            
            // Start generating observations
            collectorThread = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(2000);
                        if (running) {
                            observationsSent.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            collectorThread.setDaemon(true);
            collectorThread.start();
        }

        public void stop() {
            this.running = false;
            if (collectorThread != null) {
                collectorThread.interrupt();
            }
        }

        @Override
        public List<Observation> collect() {
            return currentDescriptor.getMetricsList().stream()
                .map(metric -> {
                    Value value;
                    if (metric.getValueType() == ValueType.VALUE_TYPE_DOUBLE) {
                        value = Value.newBuilder().setDoubleValue(Math.random() * 100).build();
                    } else if (metric.getValueType() == ValueType.VALUE_TYPE_INT64) {
                        value = Value.newBuilder().setIntValue((long) (Math.random() * 10000)).build();
                    } else {
                        value = Value.newBuilder().setDoubleValue(0).build();
                    }
                    
                    return Observation.newBuilder()
                        .setMetric(metric.getName())
                        .setValue(value)
                        .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                        .build();
                })
                .toList();
        }

        public int getObservationsSent() {
            return observationsSent.get();
        }

        public String getDescriptorVersion() {
            return descriptorVersion;
        }

        public boolean isRegistered() {
            return registered;
        }

        public boolean isRunning() {
            return running;
        }

        public List<String> getCollectedMetrics() {
            return collectedMetrics;
        }
    }
}