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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import com.platform.uop.AbstractPostgresIntegrationTest;
import com.platform.uop.IntegrationTest;

@IntegrationTest
@AutoConfigureMockMvc
class WorkspaceControllerContractTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession userSession;

    @BeforeAll
    static void setUpAll() {
    }

    @AfterAll
    static void tearDownAll() {
    }

    @BeforeEach
    void setUp() throws Exception {
        adminSession = new MockHttpSession();
        userSession = new MockHttpSession();
        
        ensureAdminAndUser();
    }

    private void ensureAdminAndUser() throws Exception {
        String adminEmail = "admin@workspace.test";
        String userEmail = "user@workspace.test";
        
        // Try to login as admin
        LoginRequest adminLogin = new LoginRequest(adminEmail, "password123");
        MvcResult adminLoginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin))
                .session(adminSession))
            .andReturn();
        
        System.out.println("Admin login attempt - status: " + adminLoginResult.getResponse().getStatus());
        
        if (adminLoginResult.getResponse().getStatus() == 200) {
            System.out.println("Admin login successful");
        } else {
            System.out.println("Admin login failed, creating admin");
            CreateAdminRequest adminRequest = new CreateAdminRequest(adminEmail, "password123");
            mockMvc.perform(post("/api/setup/admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(adminLogin))
                    .session(adminSession))
                .andExpect(status().isOk());
            System.out.println("Admin created and logged in");
        }

        // Try to login as user
        LoginRequest userLogin = new LoginRequest(userEmail, "password123");
        MvcResult userLoginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userLogin))
                .session(userSession))
            .andReturn();
        
        System.out.println("User login attempt - status: " + userLoginResult.getResponse().getStatus());
        
        if (userLoginResult.getResponse().getStatus() == 200) {
            System.out.println("User login successful");
        } else {
            System.out.println("User login failed, creating user");
            CreateUserRequest userRequest = new CreateUserRequest(userEmail, "password123");
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(userRequest))
                    .session(adminSession))
                .andExpect(status().isOk());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(userLogin))
                    .session(userSession))
                .andExpect(status().isOk());
            System.out.println("User created and logged in");
        }
    }

    // ==================== CREATE WORKSPACE ====================

    @Test
    void createWorkspace_validRequest_returns201() throws Exception {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "Test Workspace",
            "Test Description"
        );

        MvcResult result = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Test Workspace"))
            .andExpect(jsonPath("$.description").value("Test Description"))
            .andReturn();

        WorkspaceResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), WorkspaceResponse.class);
        assertThat(response.id()).isNotNull();
    }

    @Test
    void createWorkspace_emptyName_returns400() throws Exception {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "",
            "Description"
        );

        mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(adminSession))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkspace_nameTooLong_returns400() throws Exception {
        String longName = "a".repeat(101);
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            longName,
            "Description"
        );

        mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(adminSession))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkspace_descriptionTooLong_returns400() throws Exception {
        String longDesc = "a".repeat(1001);
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "Test",
            longDesc
        );

        mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(adminSession))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkspace_nullName_returns400() throws Exception {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            null,
            "Description"
        );

        mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .session(adminSession))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkspace_noAuth_returns401() throws Exception {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );

        mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    // ==================== GET WORKSPACES (admin only) ====================

    @Test
    void findAllWorkspaces_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/workspaces")
                .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void findAllWorkspaces_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/workspaces")
                .session(userSession))
            .andExpect(status().isForbidden());
    }

    // ==================== GET WORKSPACE BY ID ====================

    @Test
    void findWorkspaceById_admin_returns200() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        mockMvc.perform(get("/api/workspaces/" + created.id())
                .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id().toString()))
            .andExpect(jsonPath("$.name").value("Test Workspace"));
    }

    @Test
    void findWorkspaceById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/workspaces/" + UUID.randomUUID())
                .session(adminSession))
            .andExpect(status().isNotFound());
    }

    // ==================== UPDATE WORKSPACE ====================

    @Test
    void updateWorkspace_owner_returns200() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Original Name",
            "Original Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        UpdateWorkspaceRequest updateRequest = new UpdateWorkspaceRequest(
            "Updated Name",
            "Updated Description"
        );

        mockMvc.perform(put("/api/workspaces/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.description").value("Updated Description"));
    }

    @Test
    void updateWorkspace_emptyName_returns400() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Original Name",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        UpdateWorkspaceRequest updateRequest = new UpdateWorkspaceRequest(
            "",
            "Description"
        );

        mockMvc.perform(put("/api/workspaces/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .session(adminSession))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateWorkspace_notFound_returns404() throws Exception {
        UpdateWorkspaceRequest updateRequest = new UpdateWorkspaceRequest(
            "Updated Name",
            "Description"
        );

        mockMvc.perform(put("/api/workspaces/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .session(adminSession))
            .andExpect(status().isNotFound());
    }

    // ==================== DELETE WORKSPACE ====================

    @Test
    void deleteWorkspace_owner_returns204() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "To Delete",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        mockMvc.perform(delete("/api/workspaces/" + created.id())
                .session(adminSession))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workspaces/" + created.id())
                .session(adminSession))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteWorkspace_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/workspaces/" + UUID.randomUUID())
                .session(adminSession))
            .andExpect(status().isNotFound());
    }

    // ==================== WORKSPACE MEMBERS ====================

    @Test
    void addMember_validRequest_returns201() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        String memberEmail = "member" + System.currentTimeMillis() + "@test.com";
        CreateUserRequest userRequest = new CreateUserRequest(memberEmail, "password123");
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest))
                .session(adminSession))
            .andExpect(status().isOk());

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            memberEmail,
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );

        mockMvc.perform(post("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(memberRequest))
                .session(adminSession))
            .andExpect(status().isCreated());
    }

    @Test
    void addMember_invalidEmail_returns400() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            "not-an-email",
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );

        mockMvc.perform(post("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(memberRequest))
                .session(adminSession))
            .andExpect(status().isBadRequest());
    }

    @Test
    void addMember_nullRole_returns400() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            "user@test.com",
            null
        );

        mockMvc.perform(post("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(memberRequest))
                .session(adminSession))
            .andExpect(status().isBadRequest());
    }

    @Test
    void addMember_nonOwner_returns403() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        AddWorkspaceMemberRequest memberRequest = new AddWorkspaceMemberRequest(
            "another@test.com",
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );

        mockMvc.perform(post("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(memberRequest))
                .session(userSession))
            .andExpect(status().isForbidden());
    }

    @Test
    void findWorkspaceMembers_owner_returns200() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        mockMvc.perform(get("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FindWorkspaceMembersRequest()))
                .session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void updateMemberRole_owner_returns200() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        String memberEmail = "member" + System.currentTimeMillis() + "@test.com";
        CreateUserRequest userRequest = new CreateUserRequest(memberEmail, "password123");
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest))
                .session(adminSession))
            .andExpect(status().isOk());

        AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest(
            memberEmail,
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );
        mockMvc.perform(post("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addRequest))
                .session(adminSession))
            .andExpect(status().isCreated());

        UpdateWorkspaceMemberRequest updateRequest = new UpdateWorkspaceMemberRequest(
            memberEmail,
            com.platform.uop.workspace.enums.WorkspaceRole.ADMIN
        );

        mockMvc.perform(patch("/api/workspaces/" + created.id() + "/members/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .session(adminSession))
            .andExpect(status().isOk());
    }

    @Test
    void removeMember_owner_returns200() throws Exception {
        CreateWorkspaceRequest createRequest = new CreateWorkspaceRequest(
            "Test Workspace",
            "Description"
        );
        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .session(adminSession))
            .andExpect(status().isCreated())
            .andReturn();

        WorkspaceResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), WorkspaceResponse.class);

        String memberEmail = "toremove" + System.currentTimeMillis() + "@test.com";
        CreateUserRequest userRequest = new CreateUserRequest(memberEmail, "password123");
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest))
                .session(adminSession))
            .andExpect(status().isOk());

        AddWorkspaceMemberRequest addRequest = new AddWorkspaceMemberRequest(
            memberEmail,
            com.platform.uop.workspace.enums.WorkspaceRole.MEMBER
        );
        mockMvc.perform(post("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addRequest))
                .session(adminSession))
            .andExpect(status().isCreated());

        RemoveWorkspaceMemeberRequest removeRequest = new RemoveWorkspaceMemeberRequest(
            memberEmail
        );

        mockMvc.perform(delete("/api/workspaces/" + created.id() + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(removeRequest))
                .session(adminSession))
            .andExpect(status().isOk());
    }
}