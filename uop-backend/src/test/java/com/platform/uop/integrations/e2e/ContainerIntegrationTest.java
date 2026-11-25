package com.platform.uop.integrations.e2e;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.AgentCredentialRepository;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.MonitorService;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.observation.repository.ObservationBatchRepository;
import com.platform.uop.observation.repository.ObservationRepository;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;
import com.platform.uop.workspace.dto.AddWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.FindWorkspaceMembersRequest;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;
import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.awaitility.Awaitility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.platform.uop.IntegrationTest;

@IntegrationTest
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
class ContainerIntegrationTest {

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

    @Autowired
    private AgentCredentialRepository agentCredentialRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private UserRepository userRepository;

    private String adminSessionCookie;
    private UUID workspaceId;
    private UUID monitorId;
    private String registrationToken;

    private static final String ADMIN_EMAIL = "admin@container.test";
    private static final String ADMIN_PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        // Clean database in FK-safe order so each test starts isolated.
        observationRepository.deleteAll();
        observationBatchRepository.deleteAll();
        agentCredentialRepository.deleteAll();
        monitorRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        adminSessionCookie = loginAsAdmin();
        
        workspaceId = createWorkspace("Container Test Workspace");
        try {
            monitorId = createMonitorViaService(workspaceId.toString(), "Container Test Monitor");
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        
        String installCommand = monitorService.generateInstallationCommand(workspaceId, monitorId);
        registrationToken = extractRegistrationToken(installCommand);
    }

    @Test
    void containerSetup_apiWorksWithTestcontainersPostgres() {
        // Test that the backend works with testcontainers PostgreSQL
        Response response = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .when()
            .get("/workspaces/" + workspaceId)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getString("id")).isEqualTo(workspaceId.toString());
    }

    @Test
    void containerSetup_monitorCanBeCreatedAndQueried() {
        // Verify monitor exists and can be queried
        Monitor monitor = monitorRepository.findById(monitorId).orElse(null);
        assertThat(monitor).isNotNull();
        assertThat(monitor.getDescriptor().getName()).isEqualTo("Container Test Monitor");
        assertThat(monitor.getDescriptor().getVersion()).isEqualTo("1.0");
    }

    @Test
    void containerSetup_registrationTokenGenerated() {
        // Verify registration token is generated
        assertThat(registrationToken).isNotNull();
        assertThat(registrationToken).isNotEmpty();
    }

    @Test
    void containerSetup_monitorDescriptorHasMetrics() {
        Monitor monitor = monitorRepository.findById(monitorId).orElse(null);
        assertThat(monitor).isNotNull();
        assertThat(monitor.getDescriptor().getMetricsCount()).isGreaterThan(0);
        
        boolean hasCpuMetric = false;
        for (int i = 0; i < monitor.getDescriptor().getMetricsCount(); i++) {
            if ("cpu.usage".equals(monitor.getDescriptor().getMetrics(i).getName())) {
                hasCpuMetric = true;
                assertThat(monitor.getDescriptor().getMetrics(i).getValueType()).isEqualTo(ValueType.VALUE_TYPE_DOUBLE);
                assertThat(monitor.getDescriptor().getMetrics(i).getMetricType()).isEqualTo(MetricType.METRIC_TYPE_GAUGE);
                assertThat(monitor.getDescriptor().getMetrics(i).getUnit()).isEqualTo("percent");
                break;
            }
        }
        assertThat(hasCpuMetric).isTrue();
    }

    @Test
    void containerSetup_canCreateMultipleWorkspacesAndMonitors() {
        // Create second workspace
        UUID ws2 = createWorkspace("Second Workspace");
        assertThat(ws2).isNotEqualTo(workspaceId);
        
        // Create monitor in second workspace
        UUID mon2;
        try {
            mon2 = createMonitorViaService(ws2.toString(), "Second Monitor");
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        assertThat(mon2).isNotEqualTo(monitorId);
        
        // Verify both exist
        assertThat(workspaceRepository.findById(ws2)).isPresent();
        assertThat(monitorRepository.findById(mon2)).isPresent();
    }

    @Test
    void containerSetup_workspaceMembersCanBeManaged() {
        // Create another user
        CreateUserRequest userRequest = new CreateUserRequest("member@container.test", "pass123");
        Response userResponse = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(userRequest)
            .when()
            .post("/users")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        UUID memberId = UUID.fromString(userResponse.jsonPath().getString("id"));
        
        // Add member to workspace
        AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest("member@container.test", WorkspaceRole.MEMBER);
        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(addRequest)
            .when()
            .post("/workspaces/" + workspaceId + "/members")
            .then()
            .statusCode(201)
            .body("role", org.hamcrest.Matchers.equalTo("MEMBER"));
        
        // Verify member exists
        FindWorkspaceMembersRequest findRequest = new FindWorkspaceMembersRequest();
        Response membersResponse = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(findRequest)
            .when()
            .get("/workspaces/" + workspaceId + "/members")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(membersResponse.jsonPath().getList("$").size()).isGreaterThanOrEqualTo(2);
    }

    private String loginAsAdmin() {
        String adminEmail = ADMIN_EMAIL;
        String password = ADMIN_PASSWORD;
        
        // Try to create admin (will return 409 if exists)
        Response setupResponse = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"" + adminEmail + "\",\"password\":\"" + password + "\"}")
            .when()
            .post("/setup/admin");
        
        if (setupResponse.statusCode() != 200 && setupResponse.statusCode() != 409) {
            throw new AssertionError("Setup failed with status " + setupResponse.statusCode() + ". Body: " + setupResponse.getBody().asString());
        }

        // If 409, the admin already exists but we need to login
        // Login
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
            .setId("container-monitor-" + UUID.randomUUID())
            .setName(name)
            .setDescription("Container Test Monitor")
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("--registration-token\\s+['\"]?(\\S+?)['\"]?(\\s|$)");
        java.util.regex.Matcher matcher = pattern.matcher(installCommand);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}