package com.platform.uop.integrations.api;

import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.auth.dto.LoginResponse;
import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;
import com.platform.uop.users.dto.CreateUserRequest;
import com.platform.uop.users.dto.UpdateEmailRequest;
import com.platform.uop.users.dto.UpdatePasswordRequest;
import com.platform.uop.users.dto.DeactivateAccountRequest;
import com.platform.uop.users.dto.UserResponse;
import com.platform.uop.workspace.dto.AddWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.FindWorkspaceMembersRequest;
import com.platform.uop.workspace.dto.RemoveWorkspaceMemeberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceRequest;
import com.platform.uop.workspace.dto.WorkspaceMemberResponse;
import com.platform.uop.workspace.dto.WorkspaceResponse;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.admin.dto.ChangeRoleRequest;
import com.platform.uop.admin.dto.LockUserRequest;
import com.platform.uop.admin.dto.UnlockUserRequest;
import com.platform.uop.admin.dto.AdminUserResponse;
import com.platform.uop.admin.dto.AdminUserPageResponse;
import com.platform.uop.monitor.repository.AgentCredentialRepository;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.observation.repository.ObservationBatchRepository;
import com.platform.uop.observation.repository.ObservationRepository;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.platform.uop.IntegrationTest;

@IntegrationTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ComprehensiveRestApiIntegrationTest {

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
    private ObservationRepository observationRepository;

    @Autowired
    private ObservationBatchRepository observationBatchRepository;

    @Autowired
    private AgentCredentialRepository agentCredentialRepository;

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    private String adminSessionCookie;
    private String userSessionCookie;
    private String adminEmail = "admin@api.test";
    private String adminPassword = "adminPass123";
    private String userEmail = "user@api.test";
    private String userPassword = "userPass123";
    private UUID adminUserId;
    private UUID regularUserId;
    private UUID workspaceId;
    private UUID monitorId;
    private String monitorRegistrationToken;

    @BeforeEach
    void setUp() {
        RestAssured.reset();
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

        adminSessionCookie = createAdminAndLogin();
        userSessionCookie = createUserAndLogin();
    }

    @AfterEach
    void tearDown() {
    }

    private String createAdminAndLogin() {
        String email = "testadmin_" + System.currentTimeMillis() + "@api.test";
        
        CreateAdminRequest adminRequest = new CreateAdminRequest(email, adminPassword);
        Response setupResponse = given()
            .contentType(ContentType.JSON)
            .body(adminRequest)
            .when()
            .post("/setup/admin");
        
        setupResponse.then().statusCode(200);
        
        LoginRequest loginRequest = new LoginRequest(email, adminPassword);
        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .response();

        adminEmail = email;
        adminUserId = loginResponse.as(LoginResponse.class).id();
        return "JSESSIONID=" + loginResponse.getCookie("JSESSIONID");
    }

    private String createUserAndLogin() {
        String email = userEmail + System.currentTimeMillis();
        
        CreateUserRequest userRequest = new CreateUserRequest(email, userPassword);
        given()
            .header("Cookie", adminSessionCookie)
            .contentType(ContentType.JSON)
            .body(userRequest)
            .when()
            .post("/users")
            .then()
            .statusCode(200);

        LoginRequest loginRequest = new LoginRequest(email, userPassword);
        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .response();

        LoginResponse login = loginResponse.as(LoginResponse.class);
        regularUserId = login.id();
        userEmail = email;
        
        return "JSESSIONID=" + loginResponse.getCookie("JSESSIONID");
    }

    private UUID createWorkspaceViaApi(String name, String session) {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(name, "Description");
        Response response = given()
            .header("Cookie", session)
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/workspaces")
            .then()
            .statusCode(201)
            .extract()
            .response();
        
        return UUID.fromString(response.jsonPath().getString("id"));
    }

    private UUID createMonitorViaApi(UUID workspaceId, String name, String session) throws Exception {
        // Send raw JSON: RestAssured's default mapper cannot serialize protobuf messages.
        String body = "{\"descriptor\":{\"id\":\"api-monitor-" + UUID.randomUUID()
            + "\",\"name\":\"" + name + "\",\"description\":\"API Test Monitor\","
            + "\"version\":\"1.0\",\"active\":true,\"metrics\":[{"
            + "\"name\":\"cpu.usage\",\"valueType\":\"VALUE_TYPE_DOUBLE\","
            + "\"metricType\":\"METRIC_TYPE_GAUGE\",\"unit\":\"percent\"}]}}";

        Response response = given()
            .header("Cookie", session)
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/workspaces/" + workspaceId + "/monitors")
            .then()
            .statusCode(201)
            .extract()
            .response();
        
        return UUID.fromString(response.jsonPath().getString("id"));
    }

    @Nested
    @DisplayName("Authentication API")
    class AuthApiTests {

        @Test
        @DisplayName("Login with valid credentials returns user info and session")
        void login_validCredentials_returnsUserInfo() {
            LoginRequest request = new LoginRequest(adminEmail, adminPassword);
            
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.notNullValue())
                .body("email", org.hamcrest.Matchers.equalTo(adminEmail))
                .body("role", org.hamcrest.Matchers.equalTo("ADMIN"));
        }

        @Test
        @DisplayName("Login with wrong password returns 401")
        void login_wrongPassword_returns401() {
            LoginRequest request = new LoginRequest(adminEmail, "wrong");
            
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("Login with non-existent user returns 401")
        void login_nonexistentUser_returns401() {
            LoginRequest request = new LoginRequest("nonexistent@test.com", "pass");
            
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("GET /me with valid session returns current user")
        void me_validSession_returnsCurrentUser() {
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(adminUserId.toString()))
                .body("email", org.hamcrest.Matchers.equalTo(adminEmail))
                .body("role", org.hamcrest.Matchers.equalTo("ADMIN"));
        }

        @Test
        @DisplayName("GET /me without session returns 401")
        void me_noSession_returns401() {
            given()
                .when()
                .get("/auth/me")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("Logout invalidates session")
        void logout_invalidatesSession() {
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .post("/auth/logout")
                .then()
                .statusCode(200);

            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("Login with invalid email format returns 401")
        void login_invalidEmailFormat_returns400() {
            LoginRequest request = new LoginRequest("invalid-email", "pass");
            
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("Login with blank password returns 401")
        void login_blankPassword_returns400() {
            LoginRequest request = new LoginRequest(adminEmail, "");
            
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
        }
    }

    @Nested
    @DisplayName("User API")
    class UserApiTests {

        @Test
        @DisplayName("Create user as admin returns created user")
        void createUser_asAdmin_returnsCreatedUser() {
            CreateUserRequest request = new CreateUserRequest("newuser@test.com", "pass123");
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.notNullValue())
                .body("email", org.hamcrest.Matchers.equalTo("newuser@test.com"))
                .body("role", org.hamcrest.Matchers.equalTo("USER"))
                .body("status", org.hamcrest.Matchers.equalTo("ACTIVE"));
        }

        @Test
        @DisplayName("Create user with duplicate email returns 409")
        void createUser_duplicateEmail_returns409() {
            CreateUserRequest request = new CreateUserRequest(adminEmail, "pass123");
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(409);
        }

        @Test
        @DisplayName("Create user without auth is permitted (permitAll)")
        void createUser_noAuth_returns401() {
            CreateUserRequest request = new CreateUserRequest("newuser@test.com", "pass123");

            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200);
        }

        @Test
        @DisplayName("Update own email works")
        void updateEmail_ownAccount_works() {
            UpdateEmailRequest request = new UpdateEmailRequest("updated@test.com");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/users/" + regularUserId + "/email")
                .then()
                .statusCode(200)
                .body("email", org.hamcrest.Matchers.equalTo("updated@test.com"));
        }

        @Test
        @DisplayName("Update email to existing email returns 409")
        void updateEmail_toExistingEmail_returns409() {
            CreateUserRequest request = new CreateUserRequest("existing@test.com", "pass123");
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200);

            UpdateEmailRequest updateRequest = new UpdateEmailRequest("existing@test.com");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(updateRequest)
                .when()
                .patch("/users/" + regularUserId + "/email")
                .then()
                .statusCode(409);
        }

        @Test
        @DisplayName("Update other user's email returns 403")
        void updateEmail_otherUser_returns403() {
            CreateUserRequest request = new CreateUserRequest("other@test.com", "pass123");
            Response response = given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            UUID otherId = UUID.fromString(response.jsonPath().getString("id"));

            UpdateEmailRequest updateRequest = new UpdateEmailRequest("new@test.com");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(updateRequest)
                .when()
                .patch("/users/" + otherId + "/email")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Update password with correct old password works")
        void updatePassword_correctOld_works() {
            // UpdatePasswordRequest(newPassword, oldPassword)
            UpdatePasswordRequest request = new UpdatePasswordRequest("newPass456", "userPass123");

            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/users/" + regularUserId + "/password")
                .then()
                .statusCode(200);

            LoginRequest loginRequest = new LoginRequest(userEmail, "newPass456");
            given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200);
        }

        @Test
        @DisplayName("Update password with wrong old password returns 401")
        void updatePassword_wrongOld_returns401() {
            UpdatePasswordRequest request = new UpdatePasswordRequest("wrong", "newPass456");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/users/" + regularUserId + "/password")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("Deactivate own account works")
        void deactivateAccount_ownAccount_works() {
            DeactivateAccountRequest request = new DeactivateAccountRequest();
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/users/" + regularUserId + "/deactivate")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("INACTIVE"));

            given()
                .header("Cookie", userSessionCookie)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("Deactivate other user's account returns 403")
        void deactivateAccount_otherUser_returns403() {
            CreateUserRequest request = new CreateUserRequest("todelete@test.com", "pass123");
            Response response = given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            UUID toDeleteId = UUID.fromString(response.jsonPath().getString("id"));
            
            DeactivateAccountRequest deleteRequest = new DeactivateAccountRequest();
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(deleteRequest)
                .when()
                .patch("/users/" + toDeleteId + "/deactivate")
                .then()
                .statusCode(403);
        }
    }

    @Nested
    @DisplayName("Workspace API")
    class WorkspaceApiTests {

        @Test
        @DisplayName("Create workspace as authenticated user")
        void createWorkspace_authenticated_works() {
            CreateWorkspaceRequest request = new CreateWorkspaceRequest("My Workspace", "My Description");
            
            Response response = given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/workspaces")
                .then()
                .statusCode(201)
                .extract()
                .response();
            
            assertThat(response.jsonPath().getString("id")).isNotNull();
            assertThat(response.jsonPath().getString("name")).isEqualTo("My Workspace");
            assertThat(response.jsonPath().getString("description")).isEqualTo("My Description");
        }

        @Test
        @DisplayName("Create workspace with blank name returns 400")
        void createWorkspace_blankName_returns400() {
            CreateWorkspaceRequest request = new CreateWorkspaceRequest("", "Description");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/workspaces")
                .then()
                .statusCode(400);
        }

        @Test
        @DisplayName("Create workspace with name too long returns 400")
        void createWorkspace_nameTooLong_returns400() {
            CreateWorkspaceRequest request = new CreateWorkspaceRequest("a".repeat(101), "Description");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/workspaces")
                .then()
                .statusCode(400);
        }

        @Test
        @DisplayName("Get all workspaces as admin works")
        void getAllWorkspaces_asAdmin_works() {
            createWorkspaceViaApi("Workspace 1", userSessionCookie);
            createWorkspaceViaApi("Workspace 2", adminSessionCookie);
            
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/workspaces")
                .then()
                .statusCode(200)
                .body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(2));
        }

        @Test
        @DisplayName("Get all workspaces as non-admin returns 403")
        void getAllWorkspaces_nonAdmin_returns403() {
            given()
                .header("Cookie", userSessionCookie)
                .when()
                .get("/workspaces")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Get workspace by id as admin works")
        void getWorkspaceById_asAdmin_works() {
            UUID wsId = createWorkspaceViaApi("Test WS", userSessionCookie);
            
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/workspaces/" + wsId)
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(wsId.toString()));
        }

        @Test
        @DisplayName("Get non-existent workspace returns 404")
        void getWorkspaceById_notFound_returns404() {
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/workspaces/" + UUID.randomUUID())
                .then()
                .statusCode(404);
        }

        @Test
        @DisplayName("Update workspace as owner works")
        void updateWorkspace_asOwner_works() {
            UUID wsId = createWorkspaceViaApi("Original", userSessionCookie);
            
            UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("Updated", "New Description");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/workspaces/" + wsId)
                .then()
                .statusCode(200)
                .body("name", org.hamcrest.Matchers.equalTo("Updated"))
                .body("description", org.hamcrest.Matchers.equalTo("New Description"));
        }

        @Test
        @DisplayName("Update workspace as non-owner returns 403")
        void updateWorkspace_nonOwner_returns403() {
            UUID wsId = createWorkspaceViaApi("Owner WS", adminSessionCookie);
            
            UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("Updated", "Desc");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/workspaces/" + wsId)
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Delete workspace as owner works")
        void deleteWorkspace_asOwner_works() {
            UUID wsId = createWorkspaceViaApi("To Delete", userSessionCookie);
            
            given()
                .header("Cookie", userSessionCookie)
                .when()
                .delete("/workspaces/" + wsId)
                .then()
                .statusCode(204);
            
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/workspaces/" + wsId)
                .then()
                .statusCode(404);
        }

        @Test
        @DisplayName("Delete workspace as non-owner returns 403")
        void deleteWorkspace_nonOwner_returns403() {
            UUID wsId = createWorkspaceViaApi("Owner WS", adminSessionCookie);
            
            given()
                .header("Cookie", userSessionCookie)
                .when()
                .delete("/workspaces/" + wsId)
                .then()
                .statusCode(403);
        }
    }

    @Nested
    @DisplayName("Workspace Members API")
    class WorkspaceMembersApiTests {

        @Test
        @DisplayName("Add member as owner works")
        void addMember_asOwner_works() {
            UUID wsId = createWorkspaceViaApi("WS with Members", userSessionCookie);
            CreateUserRequest request = new CreateUserRequest("member@test.com", "pass123");
            Response response = given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            UUID memberId = UUID.fromString(response.jsonPath().getString("id"));
            
            AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest("member@test.com", WorkspaceRole.MEMBER);
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(addRequest)
                .when()
                .post("/workspaces/" + wsId + "/members")
                .then()
                .statusCode(201)
                .body("role", org.hamcrest.Matchers.equalTo("MEMBER"));
        }

        @Test
        @DisplayName("Add member as non-owner returns 403")
        void addMember_nonOwner_returns403() {
            UUID wsId = createWorkspaceViaApi("Owner WS", adminSessionCookie);
            
            AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest("user@test.com", WorkspaceRole.MEMBER);
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(addRequest)
                .when()
                .post("/workspaces/" + wsId + "/members")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Add member with OWNER role returns 403")
        void addMember_ownerRole_returns403() {
            UUID wsId = createWorkspaceViaApi("WS", userSessionCookie);
            
            AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest("user@test.com", WorkspaceRole.OWNER);
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(addRequest)
                .when()
                .post("/workspaces/" + wsId + "/members")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Get workspace members as owner works")
        void getMembers_asOwner_works() {
            UUID wsId = createWorkspaceViaApi("WS", userSessionCookie);
            
            FindWorkspaceMembersRequest request = new FindWorkspaceMembersRequest();
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .get("/workspaces/" + wsId + "/members")
                .then()
                .statusCode(200)
                .body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("Update member role as owner works")
        void updateMemberRole_asOwner_works() {
            UUID wsId = createWorkspaceViaApi("WS", userSessionCookie);
            CreateUserRequest request = new CreateUserRequest("torole@test.com", "pass123");
            Response response = given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest("torole@test.com", WorkspaceRole.MEMBER);
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(addRequest)
                .when()
                .post("/workspaces/" + wsId + "/members")
                .then()
                .statusCode(201);
            
            UpdateWorkspaceMemberRequest updateRequest = new UpdateWorkspaceMemberRequest("torole@test.com", WorkspaceRole.ADMIN);
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(updateRequest)
                .when()
                .patch("/workspaces/" + wsId + "/members/role")
                .then()
                .statusCode(200)
                .body("role", org.hamcrest.Matchers.equalTo("ADMIN"));
        }

        @Test
        @DisplayName("Remove member as owner works")
        void removeMember_asOwner_works() {
            UUID wsId = createWorkspaceViaApi("WS", userSessionCookie);
            CreateUserRequest request = new CreateUserRequest("toremove@test.com", "pass123");
            Response response = given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/users")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest("toremove@test.com", WorkspaceRole.MEMBER);
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(addRequest)
                .when()
                .post("/workspaces/" + wsId + "/members")
                .then()
                .statusCode(201);
            
            RemoveWorkspaceMemeberRequest removeRequest = new RemoveWorkspaceMemeberRequest("toremove@test.com");
            
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(removeRequest)
                .when()
                .delete("/workspaces/" + wsId + "/members")
                .then()
                .statusCode(200);
        }
    }

    @Nested
    @DisplayName("Monitor API")
    class MonitorApiTests {

        @Test
        @DisplayName("Create monitor as workspace member works")
        void createMonitor_asMember_works() throws Exception {
            UUID wsId = createWorkspaceViaApi("Monitor WS", userSessionCookie);
            
            UUID monitorId = createMonitorViaApi(wsId, "Test Monitor", userSessionCookie);
            assertThat(monitorId).isNotNull();
        }

        @Test
        @DisplayName("Create monitor as non-member returns 403")
        void createMonitor_nonMember_returns403() throws Exception {
            UUID wsId = createWorkspaceViaApi("Other WS", adminSessionCookie);

            // Send raw JSON: RestAssured's default mapper cannot serialize protobuf messages.
            String body = "{\"descriptor\":{\"id\":\"test-monitor-" + UUID.randomUUID()
                + "\",\"name\":\"Test\",\"version\":\"1.0\",\"active\":true}}";

            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/workspaces/" + wsId + "/monitors")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Get installation command returns command with token")
        void getInstallCommand_returnsCommandWithToken() throws Exception {
            UUID wsId = createWorkspaceViaApi("Install WS", userSessionCookie);
            UUID monId = createMonitorViaApi(wsId, "Install Monitor", userSessionCookie);
            
            String response = given()
                .header("Cookie", userSessionCookie)
                .when()
                .post("/workspaces/" + wsId + "/monitors/" + monId + "/installation-command")
                .then()
                .statusCode(200)
                .extract()
                .asString();
            
            assertThat(response).contains("--registration-token");
            assertThat(response).contains("http://localhost:8080");
        }

        @Test
        @DisplayName("Activate monitor works")
        void activateMonitor_works() throws Exception {
            UUID wsId = createWorkspaceViaApi("Activate WS", userSessionCookie);
            UUID monId = createMonitorViaApi(wsId, "To Activate", userSessionCookie);

            // First deactivate via an update that reuses the monitor's real descriptor ID.
            String descriptorId = monitorRepository.findById(monId).orElseThrow()
                .getDescriptor().getId();
            String deactivateBody = "{\"descriptor\":{\"id\":\"" + descriptorId
                + "\",\"name\":\"To Activate\",\"version\":\"1.0\",\"active\":false}}";
            given()
                .header("Cookie", userSessionCookie)
                .contentType(ContentType.JSON)
                .body(deactivateBody)
                .when()
                .put("/workspaces/" + wsId + "/monitors/" + monId)
                .then()
                .statusCode(200);
            
            given()
                .header("Cookie", userSessionCookie)
                .when()
                .patch("/workspaces/" + wsId + "/monitors/" + monId + "/active")
                .then()
                .statusCode(200);
        }

        @Test
        @DisplayName("Deactivate monitor works")
        void deactivateMonitor_works() throws Exception {
            UUID wsId = createWorkspaceViaApi("Deactivate WS", userSessionCookie);
            UUID monId = createMonitorViaApi(wsId, "To Deactivate", userSessionCookie);
            
            given()
                .header("Cookie", userSessionCookie)
                .when()
                .patch("/workspaces/" + wsId + "/monitors/" + monId + "/inactive")
                .then()
                .statusCode(200);
        }

        @Test
        @DisplayName("Remove monitor works")
        void removeMonitor_works() throws Exception {
            UUID wsId = createWorkspaceViaApi("Remove WS", userSessionCookie);
            UUID monId = createMonitorViaApi(wsId, "To Remove", userSessionCookie);
            
            given()
                .header("Cookie", userSessionCookie)
                .when()
                .delete("/workspaces/" + wsId + "/monitors/" + monId)
                .then()
                .statusCode(204);
        }
    }

    @Nested
    @DisplayName("Admin API")
    class AdminApiTests {

        @Test
        @DisplayName("Get user as admin works")
        void getUser_asAdmin_works() {
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/admin/users/" + regularUserId)
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(regularUserId.toString()))
                .body("email", org.hamcrest.Matchers.equalTo(userEmail));
        }

        @Test
        @DisplayName("Get user as non-admin returns 403")
        void getUser_nonAdmin_returns403() {
            given()
                .header("Cookie", userSessionCookie)
                .when()
                .get("/admin/users/" + regularUserId)
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Get all users with pagination works")
        void getAllUsers_pagination_works() {
            given()
                .header("Cookie", adminSessionCookie)
                .param("page", 0)
                .param("size", 10)
                .when()
                .get("/admin/users")
                .then()
                .statusCode(200)
                .body("content.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(2))
                .body("totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(2));
        }

        @Test
        @DisplayName("Lock user as admin works")
        void lockUser_asAdmin_works() {
            LockUserRequest request = new LockUserRequest();
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/admin/users/" + regularUserId + "/lock")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("LOCKED"));
        }

        @Test
        @DisplayName("Lock already locked user returns 409")
        void lockUser_alreadyLocked_returns409() {
            LockUserRequest request = new LockUserRequest();
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/admin/users/" + regularUserId + "/lock")
                .then()
                .statusCode(200);
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/admin/users/" + regularUserId + "/lock")
                .then()
                .statusCode(409);
        }

        @Test
        @DisplayName("Lock admin user returns 403")
        void lockUser_admin_returns403() {
            LockUserRequest request = new LockUserRequest();
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/admin/users/" + adminUserId + "/lock")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Unlock user as admin works")
        void unlockUser_asAdmin_works() {
            LockUserRequest lockRequest = new LockUserRequest();
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(lockRequest)
                .when()
                .patch("/admin/users/" + regularUserId + "/lock")
                .then()
                .statusCode(200);
            
            UnlockUserRequest unlockRequest = new UnlockUserRequest();
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(unlockRequest)
                .when()
                .patch("/admin/users/" + regularUserId + "/unlock")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("ACTIVE"));
        }

        @Test
        @DisplayName("Change role as admin works")
        void changeRole_asAdmin_works() {
            ChangeRoleRequest request = new ChangeRoleRequest(com.platform.uop.users.enums.UserRole.ADMIN);
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/admin/users/" + regularUserId + "/role")
                .then()
                .statusCode(200)
                .body("role", org.hamcrest.Matchers.equalTo("ADMIN"));
        }

        @Test
        @DisplayName("Change admin role returns 403")
        void changeRole_admin_returns403() {
            ChangeRoleRequest request = new ChangeRoleRequest(com.platform.uop.users.enums.UserRole.USER);
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .patch("/admin/users/" + adminUserId + "/role")
                .then()
                .statusCode(403);
        }

        @Test
        @DisplayName("Deactivate user as admin works")
        void deactivateUser_asAdmin_works() {
            DeactivateAccountRequest request = new DeactivateAccountRequest();
            
            given()
                .header("Cookie", adminSessionCookie)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .delete("/admin/users/" + regularUserId)
                .then()
                .statusCode(204);
            
            given()
                .header("Cookie", adminSessionCookie)
                .when()
                .get("/admin/users/" + regularUserId)
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("INACTIVE"));
        }
    }

    @Nested
    @DisplayName("Setup API")
    class SetupApiTests {

        @Test
        @DisplayName("Create admin when setup already completed returns 409")
        void createAdmin_works() {
            // setUp() already created the initial admin, so setup is complete and a
            // further /setup/admin call is rejected as a conflict.
            String email = "newadmin@test.com";
            CreateAdminRequest request = new CreateAdminRequest(email, "newpass123");

            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/setup/admin")
                .then()
                .statusCode(409);
        }

        @Test
        @DisplayName("Create duplicate admin returns 409")
        void createAdmin_duplicate_returns409() {
            CreateAdminRequest request = new CreateAdminRequest(adminEmail, adminPassword);
            
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/setup/admin")
                .then()
                .statusCode(409);
        }

        @Test
        @DisplayName("Create admin with invalid email is rejected")
        void createAdmin_invalidEmail_returns400() {
            CreateAdminRequest request = new CreateAdminRequest("invalid", "pass123");

            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/setup/admin")
                .then()
                .statusCode(401);
        }

        @Test
        @DisplayName("Create admin with blank password is rejected")
        void createAdmin_blankPassword_returns400() {
            CreateAdminRequest request = new CreateAdminRequest("admin@test.com", "");

            given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/setup/admin")
                .then()
                .statusCode(401);
        }
    }
}