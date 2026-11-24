package com.platform.uop.integrations.api;

import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.dto.LoginResponse;
import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;
import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.WorkspaceResponse;
import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;
import com.platform.uop.proto.v1.MonitorDescriptor;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RestApiContractTest {

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

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";
    }

    @Test
    void openApiSpec_isAccessible() {
        given()
            .when()
            .get("/v3/api-docs")
            .then()
            .statusCode(200)
            .contentType(org.hamcrest.Matchers.containsString("application/json"));
    }

    @Test
    void swaggerUi_isAccessible() {
        given()
            .when()
            .get("/swagger-ui.html")
            .then()
            .statusCode(200);
    }

    @Test
    void setupAndLogin_works() {
        // Create admin
        CreateAdminRequest adminRequest = new CreateAdminRequest(
            "admin" + System.currentTimeMillis() + "@test.com",
            "password123"
        );
        SetupResponse setupResponse = given()
            .contentType(ContentType.JSON)
            .body(adminRequest)
            .when()
            .post("/setup")
            .then()
            .statusCode(200)
            .extract()
            .as(SetupResponse.class);

        assertThat(setupResponse.id()).isNotNull();
        assertThat(setupResponse.email()).isEqualTo(adminRequest.email());

        // Login
        LoginRequest loginRequest = new LoginRequest(adminRequest.email(), "password123");
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
        assertThat(loginResponse.email()).isEqualTo(adminRequest.email());
        assertThat(loginResponse.role()).isEqualTo("ADMIN");
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

    @Test
    void loginWithInvalidEmail_returns400() {
        LoginRequest invalidLogin = new LoginRequest("not-an-email", "password");
        given()
            .contentType(ContentType.JSON)
            .body(invalidLogin)
            .when()
            .post("/auth/login")
            .then()
            .statusCode(400);
    }

    @Test
    void protectedEndpoints_requireAuthentication() {
        // Access protected endpoint without token
        given()
            .when()
            .get("/auth/me")
            .then()
            .statusCode(401);

        // Access workspace without token
        given()
            .when()
            .get("/workspaces")
            .then()
            .statusCode(401);

        // Access monitors without token
        given()
            .when()
            .get("/workspaces/00000000-0000-0000-0000-000000000000/monitors")
            .then()
            .statusCode(401);
    }

    @Test
    void requestValidation_works() {
        // Create admin and login first
        CreateAdminRequest adminRequest = new CreateAdminRequest(
            "admin" + System.currentTimeMillis() + "@test.com",
            "password123"
        );
        given()
            .contentType(ContentType.JSON)
            .body(adminRequest)
            .when()
            .post("/setup")
            .then()
            .statusCode(200);

        LoginRequest loginRequest = new LoginRequest(adminRequest.email(), "password123");
        LoginResponse loginResponse = given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .as(LoginResponse.class);

        String sessionId = loginResponse.id().toString();

        // Create workspace with invalid data (empty name)
        CreateWorkspaceRequest invalidRequest = new CreateWorkspaceRequest(
            "",
            "Description"
        );

        given()
            .header("Cookie", "SESSION=" + sessionId)
            .contentType(ContentType.JSON)
            .body(invalidRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(400);
    }
}