package com.platform.uop.integrations.api;

import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.dto.LoginResponse;
import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.platform.uop.AbstractPostgresIntegrationTest;
import com.platform.uop.IntegrationTest;

@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestApiContractTest extends AbstractPostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private com.platform.uop.users.repository.UserRepository userRepository;

    @Autowired
    private com.platform.uop.workspace.repository.WorkspaceRepository workspaceRepository;

    @Autowired
    private com.platform.uop.monitor.repository.MonitorRepository monitorRepository;

    @Autowired
    private com.platform.uop.monitor.repository.AgentCredentialRepository agentCredentialRepository;

    @Autowired
    private com.platform.uop.observation.repository.ObservationRepository observationRepository;

    @Autowired
    private com.platform.uop.observation.repository.ObservationBatchRepository observationBatchRepository;

    @Autowired
    private com.platform.uop.workspace.repository.WorkspaceMemberRepository workspaceMemberRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        // /setup/admin is a one-shot operation; wipe the DB so setup can run fresh
        // even when the container/context is shared with other test classes.
        observationRepository.deleteAll();
        observationBatchRepository.deleteAll();
        agentCredentialRepository.deleteAll();
        monitorRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void setupAndLogin_works() {
        // Create admin - correct endpoint is /api/setup/admin
        String email = "admin" + System.currentTimeMillis() + "@test.com";
        CreateAdminRequest adminRequest = new CreateAdminRequest(email, "password123");
        SetupResponse setupResponse = given()
            .contentType(ContentType.JSON)
            .body(adminRequest)
            .when()
            .post("/setup/admin")
            .then()
            .statusCode(200)
            .extract()
            .as(SetupResponse.class);

        assertThat(setupResponse.id()).isNotNull();
        assertThat(setupResponse.email()).isEqualTo(email);
        assertThat(setupResponse.role()).isEqualTo(com.platform.uop.users.enums.UserRole.ADMIN);
        assertThat(setupResponse.status()).isEqualTo(com.platform.uop.users.enums.UserStatus.ACTIVE);

        // Login - this app uses session cookies, not Bearer tokens
        LoginRequest loginRequest = new LoginRequest(email, "password123");
        LoginResponse loginResponse = given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .as(LoginResponse.class);

        assertThat(loginResponse.id()).isNotNull();
        assertThat(loginResponse.email()).isEqualTo(email);
        assertThat(loginResponse.role()).isEqualTo(com.platform.uop.users.enums.UserRole.ADMIN);
    }

    @Test
    void loginWithInvalidCredentials_returns401() {
        LoginRequest invalidLogin = new LoginRequest("nonexistent@test.com", "wrongpass");
        given()
            .contentType(ContentType.JSON)
            .body(invalidLogin)
            .when()
            .post("/auth/login")
            .then()
            .statusCode(401);
    }
}