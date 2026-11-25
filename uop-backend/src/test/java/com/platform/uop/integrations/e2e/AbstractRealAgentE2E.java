package com.platform.uop.integrations.e2e;

import com.platform.uop.agent.sdk.AgentSdk;
import com.platform.uop.agent.sdk.Collector;
import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentConnectionService;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.monitor.service.AgentGrpcService;
import com.platform.uop.monitor.service.MonitorService;
import com.platform.uop.observation.repository.ObservationBatchRepository;
import com.platform.uop.observation.repository.ObservationRepository;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared infrastructure for the real-agent end-to-end tests. Each concrete
 * subclass declares its own Testcontainers PostgreSQL container and runs in its
 * own Spring context (see {@code @DirtiesContext} on the subclasses) so that
 * lingering SDK reconnect threads and connection state from one test class
 * cannot corrupt the next.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractRealAgentE2E {

    @LocalServerPort
    private int port;

    @Autowired
    protected MonitorRepository monitorRepository;

    @Autowired
    protected WorkspaceRepository workspaceRepository;

    @Autowired
    protected MonitorService monitorService;

    @Autowired
    protected AgentCredentialService agentCredentialService;

    @Autowired
    protected ObservationRepository observationRepository;

    @Autowired
    protected ObservationBatchRepository observationBatchRepository;

    @Autowired
    protected AgentGrpcService agentGrpcService;

    @Autowired
    protected AgentConnectionService agentConnectionService;

    protected Server grpcServer;
    protected int grpcPort;
    protected Path tempDir;
    protected String adminSessionCookie;
    protected UUID workspaceId;
    protected UUID monitorId;
    protected String registrationToken;
    protected AgentSdk agentSdk;

    private static final String ADMIN_EMAIL = "admin@realagent.test";
    private static final String ADMIN_PASSWORD = "password123";

    @BeforeEach
    void setUp() throws Exception {
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        // Start a real gRPC server on a random free port (the "test" profile
        // disables the production gRPC server via @Profile("!test")).
        grpcServer = ServerBuilder.forPort(0)
            .addService(agentGrpcService)
            .build()
            .start();
        grpcPort = grpcServer.getPort();

        tempDir = Files.createTempDirectory("real-agent-e2e-test");

        adminSessionCookie = loginAsAdmin();

        String workspaceName = "Real Agent E2E Workspace " + UUID.randomUUID();
        workspaceId = createWorkspace(workspaceName);

        monitorId = createMonitorViaService(workspaceId.toString(), "Real Agent Monitor");

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
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
        if (tempDir != null && Files.exists(tempDir)) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Best-effort cleanup.
                        }
                    });
            }
        }
    }

    protected AgentSdk newAgent(TestCollector collector) {
        return AgentSdk.builder()
            .platformHost("localhost")
            .platformPort(grpcPort)
            .registrationToken(registrationToken)
            .credentialsPath(tempDir.resolve("credentials"))
            .collector(collector)
            .build();
    }

    private String loginAsAdmin() {
        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
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
            .body("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
            .when()
            .post("/setup/admin");

        if (setupResponse.statusCode() != 200 && setupResponse.statusCode() != 409) {
            throw new AssertionError("Setup failed with status " + setupResponse.statusCode()
                + ". Body: " + setupResponse.getBody().asString());
        }

        Response response = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
            .when()
            .post("/auth/login");

        if (response.statusCode() != 200) {
            throw new AssertionError("Login failed with status " + response.statusCode()
                + ". Body: " + response.getBody().asString());
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

    private UUID createMonitorViaService(String workspaceId, String name)
        throws com.google.protobuf.InvalidProtocolBufferException {
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "--registration-token\\s+['\"]?(\\S+?)['\"]?(\\s|$)");
        java.util.regex.Matcher matcher = pattern.matcher(installCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // Test collector that generates sample metrics using the real SDK Collector contract.
    protected static class TestCollector implements Collector {
        private final AtomicInteger collectCount = new AtomicInteger(0);
        private final java.util.Set<String> metrics = new java.util.concurrent.ConcurrentSkipListSet<>();
        private volatile boolean running = false;

        TestCollector(String... metricNames) {
            metrics.addAll(java.util.Arrays.asList(metricNames));
        }

        @Override
        public MetricDescriptor getDescriptor() {
            return MetricDescriptor.newBuilder()
                .setName(metrics.isEmpty() ? "cpu.usage" : metrics.iterator().next())
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build();
        }

        @Override
        public java.time.Duration getInterval() {
            return java.time.Duration.ofMillis(500);
        }

        @Override
        public List<Observation> collect() {
            running = true;
            collectCount.incrementAndGet();
            return metrics.stream()
                .map(name -> Observation.newBuilder()
                    .setMetric(name)
                    .setValue(Value.newBuilder().setDoubleValue(Math.random() * 100).build())
                    .setTimestamp(com.google.protobuf.util.Timestamps
                        .fromMillis(System.currentTimeMillis()))
                    .build())
                .toList();
        }

        public int getObservationsSent() {
            return collectCount.get();
        }

        public boolean isRunning() {
            return running;
        }
    }
}