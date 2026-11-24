package com.platform.uop.integrations.api.contract.workspace;

import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.dto.LoginResponse;
import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;
import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.workspace.dto.AddWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.FindWorkspaceMembersRequest;
import com.platform.uop.workspace.dto.RemoveWorkspaceMemeberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceRequest;
import com.platform.uop.workspace.dto.WorkspaceResponse;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import static org.hamcrest.Matchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkspaceControllerContractTest {

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

    private static String adminSessionCookie;
    private static String userSessionCookie;
    private static boolean adminInitialized = false;

    @BeforeAll
    static void setUpAll() {
        // Will be initialized lazily
    }

    @AfterAll
    static void tearDownAll() {
        adminInitialized = false;
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";
        
        if (!adminInitialized) {
            ensureAdminAndUser();
            adminInitialized = true;
        }
        
        // Ensure cookies are set - if not, throw with clear message
        if (adminSessionCookie == null || adminSessionCookie.isEmpty()) {
            throw new IllegalStateException("Admin session cookie not initialized - ensureAdminAndUser failed");
        }
    }

    private void ensureAdminAndUser() {
        // Use fixed emails for consistent login
        String adminEmail = "admin@workspace.test";
        String userEmail = "user@workspace.test";
        
        // Try to login as admin
        LoginRequest adminLogin = new LoginRequest(adminEmail, "password123");
        Response adminLoginResponse = given()
            .contentType(ContentType.JSON)
            .body(adminLogin)
            .when()
            .post("/auth/login");
        
        if (adminLoginResponse.statusCode() == 200) {
            adminSessionCookie = extractSessionCookie(adminLoginResponse);
        } else {
            // Create admin with fixed email
            CreateAdminRequest adminRequest = new CreateAdminRequest(adminEmail, "password123");
            given()
                .contentType(ContentType.JSON)
                .body(adminRequest)
                .when()
                .post("/setup/admin")
                .then()
                .statusCode(200);

            // Login as admin and extract session cookie
            Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body(adminLogin)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
            adminSessionCookie = extractSessionCookie(loginResponse);
        }

        // Try to login as user
        LoginRequest userLogin = new LoginRequest(userEmail, "password123");
        Response userLoginResponse = given()
            .contentType(ContentType.JSON)
            .body(userLogin)
            .when()
            .post("/auth/login");
        
        if (userLoginResponse.statusCode() == 200) {
            userSessionCookie = extractSessionCookie(userLoginResponse);
        } else {
            // Create user via admin - UserController.create returns 200 not 201
            CreateUserRequest userRequest = new CreateUserRequest(userEmail, "password123");
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(userRequest)
                .when()
                .post("/users")
                .then()
                .statusCode(200);

            // Login as user and extract session cookie
            Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body(userLogin)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
            userSessionCookie = extractSessionCookie(loginResponse);
        }
    }

    private String extractSessionCookie(Response response) {
        // Spring Security uses JSESSIONID by default
        String cookie = response.getCookie("JSESSIONID");
        if (cookie != null && !cookie.isEmpty()) {
            return "JSESSIONID=" + cookie;
        }
        // Fallback to SESSION cookie name
        cookie = response.getCookie("SESSION");
        if (cookie != null && !cookie.isEmpty()) {
            return "SESSION=" + cookie;
        }
        // Fallback: try to get all cookies and find session
        var cookies = response.getCookies();
        for (String name : cookies.keySet()) {
            String value = cookies.get(name);
            if (name.toLowerCase().contains("session") || name.equals("JSESSIONID")) {
                return name + "=" + value;
            }
        }
        throw new IllegalStateException("No session cookie found in response. Cookies: " + cookies);
    }

    // ==================== CREATE WORKSPACE ====================

    @Test
    void createWorkspace_validRequest_returns201() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "Test Workspace",
            "Test Description"
        );

        WorkspaceResponse response = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Test Workspace"))
            .body("description", equalTo("Test Description"))
            .extract()
            .as(WorkspaceResponse.class);

        assertThat(response.id()).isNotNull();
    }

    @Test
    void createWorkspace_emptyName_returns400() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "",
            "Description"
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(400);
    }

    @Test
    void createWorkspace_nameTooLong_returns400() {
        String longName = "a".repeat(101);
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            longName,
            "Description"
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(400);
    }

    @Test
    void createWorkspace_descriptionTooLong_returns400() {
        String longDesc = "a".repeat(1001);
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "Test",
            longDesc
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(400);
    }

    @Test
    void createWorkspace_nullName_returns400() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            null,
            "Description"
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(400);
    }

    @Test
    void createWorkspace_noAuth_returns401() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(401);
    }

    // ==================== GET WORKSPACES (admin only) ====================

    @Test
    void findAllWorkspaces_admin_returns200() {
        given()
            .header("Cookie", adminSessionCookie)
            .when()
            .get("/workspaces")
            .then()
            .statusCode(200)
            .body("", hasSize(greaterThanOrEqualTo(0)));
    }

    @Test
    void findAllWorkspaces_nonAdmin_returns403() {
        given()
            .header("Cookie", userSessionCookie)
            .when()
            .get("/workspaces")
            .then()
            .statusCode(403);
    }

    // ==================== GET WORKSPACE BY ID ====================

    @Test
    void findWorkspaceById_admin_returns200() {
        // Create workspace first
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        given()
            .header("Cookie", adminSessionCookie)
            .when()
            .get("/workspaces/" + created.id())
            .then()
            .statusCode(200)
            .body("id", equalTo(created.id().toString()))
            .body("name", equalTo("Test Workspace"));
    }

    @Test
    void findWorkspaceById_notFound_returns404() {
        given()
            .header("Cookie", adminSessionCookie)
            .when()
            .get("/workspaces/" + UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    // ==================== UPDATE WORKSPACE ====================

    @Test
    void updateWorkspace_owner_returns200() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Original Name",
            "Original Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        UpdateWorkspaceRequest updateRequest = new UpdateWorkspaceRequest(
            "Updated Name",
            "Updated Description"
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .when()
            .put("/workspaces/" + created.id())
            .then()
            .statusCode(200)
            .body("name", equalTo("Updated Name"))
            .body("description", equalTo("Updated Description"));
    }

    @Test
    void updateWorkspace_emptyName_returns400() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Original Name",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        UpdateWorkspaceRequest updateRequest = new UpdateWorkspaceRequest(
            "",
            "Description"
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .when()
            .put("/workspaces/" + created.id())
            .then()
            .statusCode(400);
    }

    @Test
    void updateWorkspace_notFound_returns404() {
        UpdateWorkspaceRequest updateRequest = new UpdateWorkspaceRequest(
            "Updated Name",
            "Description"
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .when()
            .put("/workspaces/" + UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    // ==================== DELETE WORKSPACE ====================

    @Test
    void deleteWorkspace_owner_returns204() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "To Delete",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        given()
            .header("Cookie", adminSessionCookie)
            .when()
            .delete("/workspaces/" + created.id())
            .then()
            .statusCode(204);

        // Verify deleted
        given()
            .header("Cookie", adminSessionCookie)
            .when()
            .get("/workspaces/" + created.id())
            .then()
            .statusCode(404);
    }

    @Test
    void deleteWorkspace_notFound_returns404() {
        given()
            .header("Cookie", adminSessionCookie)
            .when()
            .delete("/workspaces/" + UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    // ==================== WORKSPACE MEMBERS ====================

    @Test
    void addMember_validRequest_returns201() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        System.out.println("Created workspace ID: " + created.id());

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            "member" + System.currentTimeMillis() + "@test.com",
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );

        // First, let's see what the workspace endpoint returns
        String workspaceUrl = "/workspaces/" + created.id() + "/members";
        System.out.println("Posting to: " + workspaceUrl);

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(memberRequest)
            .when()
            .post("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(201);
    }

    @Test
    void addMember_invalidEmail_returns400() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            "not-an-email",
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(memberRequest)
            .when()
            .post("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(400);
    }

    @Test
    void addMember_nullRole_returns400() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            "user@test.com",
            null
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(memberRequest)
            .when()
            .post("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(400);
    }

    @Test
    void addMember_nonOwner_returns403() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            "another@test.com",
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );

        given()
            .header("Cookie", userSessionCookie)
            .contentType(ContentType.JSON)
            .body(memberRequest)
            .when()
            .post("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(403);
    }

    @Test
    void findWorkspaceMembers_owner_returns200() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(new FindWorkspaceMembersRequest())
            .when()
            .get("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(200)
            .body("", hasSize(greaterThanOrEqualTo(1))); // at least owner
    }

    @Test
    void updateMemberRole_owner_returns200() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        // Add member first
        String memberEmail = "member" + System.currentTimeMillis() + "@test.com";
        AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest(
            memberEmail,
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );
        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(addRequest)
            .when()
            .post("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(201);

        // Update role
        UpdateWorkspaceMemberRequest updateRequest = new UpdateWorkspaceMemberRequest(
            memberEmail,
            com.platform.uop.workspace.enums.WorkspaceRole.ADMIN
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(updateRequest)
            .when()
            .patch("/workspaces/" + created.id() + "/members/role")
            .then()
            .statusCode(200);
    }

    @Test
    void removeMember_owner_returns200() {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        WorkspaceResponse created = given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .as(WorkspaceResponse.class);

        // Add member first
        String memberEmail = "toremove" + System.currentTimeMillis() + "@test.com";
        AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest(
            memberEmail,
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );
        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(addRequest)
            .when()
            .post("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(201);

        // Remove member
        RemoveWorkspaceMemeberRequest removeRequest = new RemoveWorkspaceMemeberRequest(
            memberEmail
        );

        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(removeRequest)
            .when()
            .delete("/workspaces/" + created.id() + "/members")
            .then()
            .statusCode(200);
    }
}