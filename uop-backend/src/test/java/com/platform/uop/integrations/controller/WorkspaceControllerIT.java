package com.platform.uop.integrations.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.json.JsonMapper;
import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.security.WorkspaceSecurity;
import com.platform.uop.workspace.dto.AddWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.CreateWorkspaceRequest;
import com.platform.uop.workspace.dto.FindWorkspaceMembersRequest;
import com.platform.uop.workspace.dto.RemoveWorkspaceMemeberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceMemberRequest;
import com.platform.uop.workspace.dto.UpdateWorkspaceRequest;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceControllerIT {

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

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JsonMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private WorkspaceMemberRepository workspaceMemberRepository;

  @Autowired
  private WorkspaceSecurity workspaceSecurity;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private UUID ownerId;
  private UUID adminId;
  private UUID memberId;
  private UUID viewerId;
  private UUID otherUserId;
  private UUID workspaceId;
  private MockHttpSession ownerSession;
  private MockHttpSession adminSession;
  private MockHttpSession memberSession;
  private MockHttpSession viewerSession;

  @BeforeEach
  void setUp() {
    workspaceMemberRepository.deleteAll();
    workspaceRepository.deleteAll();
    userRepository.deleteAll();

    ownerSession = new MockHttpSession();
    adminSession = new MockHttpSession();
    memberSession = new MockHttpSession();
    viewerSession = new MockHttpSession();

    User owner = User.builder()
        .id(UUID.randomUUID())
        .email("owner@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    owner = userRepository.save(owner);
    ownerId = owner.getId();

    User admin = User.builder()
        .id(UUID.randomUUID())
        .email("admin@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    admin = userRepository.save(admin);
    adminId = admin.getId();

    User member = User.builder()
        .id(UUID.randomUUID())
        .email("member@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    member = userRepository.save(member);
    memberId = member.getId();

    User viewer = User.builder()
        .id(UUID.randomUUID())
        .email("viewer@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    viewer = userRepository.save(viewer);
    viewerId = viewer.getId();

    User otherUser = User.builder()
        .id(UUID.randomUUID())
        .email("other@example.com")
        .passwordHash(passwordEncoder.encode("password123"))
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    otherUser = userRepository.save(otherUser);
    otherUserId = otherUser.getId();

    Workspace workspace = new Workspace("Test Workspace", "Test Description");
    workspace = workspaceRepository.save(workspace);
    workspaceId = workspace.getId();

    WorkspaceMember ownerMember = new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER);
    WorkspaceMember adminMember = new WorkspaceMember(workspace, admin, WorkspaceRole.ADMIN);
    WorkspaceMember memberMember = new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER);
    WorkspaceMember viewerMember = new WorkspaceMember(workspace, viewer, WorkspaceRole.VIEWER);

    workspaceMemberRepository.saveAll(List.of(ownerMember, adminMember, memberMember, viewerMember));
    workspaceMemberRepository.flush();
  }

  private void login(String email, String password, MockHttpSession session) throws Exception {
    LoginRequest request = new LoginRequest(email, password);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .session(session))
        .andExpect(status().isOk());
  }

  private MockHttpSession createAdminSession() throws Exception {
    User admin = User.builder()
        .id(UUID.randomUUID())
        .email("admin@platform.com")
        .passwordHash(passwordEncoder.encode("adminpass"))
        .role(UserRole.ADMIN)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    userRepository.save(admin);

    MockHttpSession adminSession = new MockHttpSession();
    login("admin@platform.com", "adminpass", adminSession);
    return adminSession;
  }

  @Test
  @DisplayName("Should create workspace successfully")
  void shouldCreateWorkspaceSuccessfully() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    CreateWorkspaceRequest request = new CreateWorkspaceRequest("New Workspace", "New Description");

    mockMvc.perform(post("/api/workspaces")
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("New Workspace"))
        .andExpect(jsonPath("$.description").value("New Description"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  @DisplayName("Should reject create workspace with blank name")
  void shouldRejectCreateWorkspaceWithBlankName() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    CreateWorkspaceRequest request = new CreateWorkspaceRequest("", "Description");

    mockMvc.perform(post("/api/workspaces")
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject create workspace with name too long")
  void shouldRejectCreateWorkspaceWithNameTooLong() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    CreateWorkspaceRequest request = new CreateWorkspaceRequest("a".repeat(101), "Description");

    mockMvc.perform(post("/api/workspaces")
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject create workspace without authentication")
  void shouldRejectCreateWorkspaceWithoutAuthentication() throws Exception {
    CreateWorkspaceRequest request = new CreateWorkspaceRequest("New Workspace", "Description");

    mockMvc.perform(post("/api/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Should return all workspaces for admin")
  void shouldReturnAllWorkspacesForAdmin() throws Exception {
    MockHttpSession adminSession = createAdminSession();

    mockMvc.perform(get("/api/workspaces")
            .session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Test Workspace"));
  }

  @Test
  @DisplayName("Should reject find all for non-admin")
  void shouldRejectFindAllForNonAdmin() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    mockMvc.perform(get("/api/workspaces")
            .session(ownerSession))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject find all without authentication")
  void shouldRejectFindAllWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/workspaces"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Should return workspace for admin")
  void shouldReturnWorkspaceForAdmin() throws Exception {
    MockHttpSession adminSession = createAdminSession();

    mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
            .session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(workspaceId.toString()))
        .andExpect(jsonPath("$.name").value("Test Workspace"));
  }

  @Test
  @DisplayName("Should reject find by id for non-admin")
  void shouldRejectFindByIdForNonAdmin() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
            .session(ownerSession))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should update workspace as owner")
  void shouldUpdateWorkspaceAsOwner() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("Updated Workspace", "Updated Description");

    mockMvc.perform(put("/api/workspaces/{id}", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Workspace"))
        .andExpect(jsonPath("$.description").value("Updated Description"));
  }

  @Test
  @DisplayName("Should update workspace as admin")
  void shouldUpdateWorkspaceAsAdmin() throws Exception {
    MockHttpSession adminSession = createAdminSession();

    UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("Updated Workspace", "Updated Description");

    mockMvc.perform(put("/api/workspaces/{id}", workspaceId)
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Workspace"));
  }

  @Test
  @DisplayName("Should reject update workspace as member")
  void shouldRejectUpdateWorkspaceAsMember() throws Exception {
    login("member@example.com", "password123", memberSession);

    UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("Updated Workspace", "Updated Description");

    mockMvc.perform(put("/api/workspaces/{id}", workspaceId)
            .session(memberSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject update workspace with blank name")
  void shouldRejectUpdateWorkspaceWithBlankName() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("", "Description");

    mockMvc.perform(put("/api/workspaces/{id}", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject update non-existent workspace")
  void shouldRejectUpdateNonExistentWorkspace() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("Updated", "Description");
    UUID nonExistentId = UUID.randomUUID();

    mockMvc.perform(put("/api/workspaces/{id}", nonExistentId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should delete workspace as owner")
  void shouldDeleteWorkspaceAsOwner() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
            .session(ownerSession))
        .andExpect(status().isNoContent());

    assertThat(workspaceRepository.findById(workspaceId)).isEmpty();
  }

  @Test
  @DisplayName("Should delete workspace as admin")
  void shouldDeleteWorkspaceAsAdmin() throws Exception {
    MockHttpSession adminSession = createAdminSession();

    mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
            .session(adminSession))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Should reject delete workspace as member")
  void shouldRejectDeleteWorkspaceAsMember() throws Exception {
    login("member@example.com", "password123", memberSession);

    mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
            .session(memberSession))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject delete non-existent workspace")
  void shouldRejectDeleteNonExistentWorkspace() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UUID nonExistentId = UUID.randomUUID();

    mockMvc.perform(delete("/api/workspaces/{id}", nonExistentId)
            .session(ownerSession))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should return members as owner")
  void shouldReturnMembersAsOwner() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    FindWorkspaceMembersRequest request = new FindWorkspaceMembersRequest();

    mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[?(@.role=='OWNER')]").exists())
        .andExpect(jsonPath("$[?(@.role=='ADMIN')]").exists())
        .andExpect(jsonPath("$[?(@.role=='MEMBER')]").exists())
        .andExpect(jsonPath("$[?(@.role=='VIEWER')]").exists());
  }

  @Test
  @DisplayName("Should return members as admin")
  void shouldReturnMembersAsAdmin() throws Exception {
    login("admin@example.com", "password123", adminSession);

    FindWorkspaceMembersRequest request = new FindWorkspaceMembersRequest();

    mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));
  }

  @Test
  @DisplayName("Should reject find members as member")
  void shouldRejectFindMembersAsMember() throws Exception {
    login("member@example.com", "password123", memberSession);

    FindWorkspaceMembersRequest request = new FindWorkspaceMembersRequest();

    mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)
            .session(memberSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject find members as viewer")
  void shouldRejectFindMembersAsViewer() throws Exception {
    login("viewer@example.com", "password123", viewerSession);

    FindWorkspaceMembersRequest request = new FindWorkspaceMembersRequest();

    mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)
            .session(viewerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should add member as owner")
  void shouldAddMemberAsOwner() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("other@example.com", WorkspaceRole.MEMBER);

    mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("MEMBER"));

    assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, otherUserId)).isTrue();
  }

  @Test
  @DisplayName("Should add member as admin")
  void shouldAddMemberAsAdmin() throws Exception {
    login("admin@example.com", "password123", adminSession);

    AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("other@example.com", WorkspaceRole.MEMBER);

    mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("MEMBER"));
  }

  @Test
  @DisplayName("Should reject add member as member")
  void shouldRejectAddMemberAsMember() throws Exception {
    login("member@example.com", "password123", memberSession);

    AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("other@example.com", WorkspaceRole.MEMBER);

    mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
            .session(memberSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject add member with owner role")
  void shouldRejectAddMemberWithOwnerRole() throws Exception {
    User owner = userRepository.findByEmail("owner@example.com").orElseThrow();
    UserPrincipal principal = new UserPrincipal(owner);

    // Debug: check WorkspaceSecurity directly
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = 
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            principal, null, principal.getAuthorities());
    System.out.println("WorkspaceSecurity.canManageMembers: " + workspaceSecurity.canManageMembers(workspaceId, auth));
    System.out.println("WorkspaceSecurity.isOwner: " + workspaceSecurity.isOwner(workspaceId, auth));
    System.out.println("WorkspaceSecurity.isMember: " + workspaceSecurity.isMember(workspaceId, auth));

    AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("other@example.com", WorkspaceRole.OWNER);

    MvcResult result = mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
            .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andReturn();

    System.out.println("Status: " + result.getResponse().getStatus());
    System.out.println("Body: " + result.getResponse().getContentAsString());

    mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
            .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Cannot grant owner permission"));
  }

  @Test
  @DisplayName("Should reject add member with invalid email")
  void shouldRejectAddMemberWithInvalidEmail() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("invalid-email", WorkspaceRole.MEMBER);

    mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should reject add non-existent user")
  void shouldRejectAddNonExistentUser() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("nonexistent@example.com", WorkspaceRole.MEMBER);

    mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  @DisplayName("Should reject add member to non-existent workspace")
  void shouldRejectAddMemberToNonExistentWorkspace() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest("other@example.com", WorkspaceRole.MEMBER);
    UUID nonExistentId = UUID.randomUUID();

    mockMvc.perform(post("/api/workspaces/{id}/members", nonExistentId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should update member role as owner")
  void shouldUpdateMemberRoleAsOwner() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("member@example.com", WorkspaceRole.ADMIN);

    mockMvc.perform(patch("/api/workspaces/{id}/members/role", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));

    WorkspaceMember member = workspaceMemberRepository
        .findByWorkspaceIdAndUserEmail(workspaceId, "member@example.com")
        .orElseThrow();
    assertThat(member.getRole()).isEqualTo(WorkspaceRole.ADMIN);
  }

  @Test
  @DisplayName("Should update member role as admin")
  void shouldUpdateMemberRoleAsAdmin() throws Exception {
    login("admin@example.com", "password123", adminSession);

    UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("member@example.com", WorkspaceRole.ADMIN);

    mockMvc.perform(patch("/api/workspaces/{id}/members/role", workspaceId)
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  @DisplayName("Should reject update role as member")
  void shouldRejectUpdateRoleAsMember() throws Exception {
    login("member@example.com", "password123", memberSession);

    UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("viewer@example.com", WorkspaceRole.ADMIN);

    mockMvc.perform(patch("/api/workspaces/{id}/members/role", workspaceId)
            .session(memberSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject update to owner role")
  void shouldRejectUpdateToOwnerRole() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("member@example.com", WorkspaceRole.OWNER);

    mockMvc.perform(patch("/api/workspaces/{id}/members/role", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Cannot grant owner permission"));
  }

  @Test
  @DisplayName("Should reject update non-existent member")
  void shouldRejectUpdateNonExistentMember() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("nonexistent@example.com", WorkspaceRole.ADMIN);

    mockMvc.perform(patch("/api/workspaces/{id}/members/role", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  @DisplayName("Should reject update with invalid email")
  void shouldRejectUpdateWithInvalidEmail() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    UpdateWorkspaceMemberRequest request = new UpdateWorkspaceMemberRequest("invalid-email", WorkspaceRole.ADMIN);

    mockMvc.perform(patch("/api/workspaces/{id}/members/role", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Should remove member as owner")
  void shouldRemoveMemberAsOwner() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("member@example.com");

    mockMvc.perform(delete("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, memberId)).isFalse();
  }

  @Test
  @DisplayName("Should remove member as admin")
  void shouldRemoveMemberAsAdmin() throws Exception {
    login("admin@example.com", "password123", adminSession);

    RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("member@example.com");

    mockMvc.perform(delete("/api/workspaces/{id}/members", workspaceId)
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Should reject remove member as member")
  void shouldRejectRemoveMemberAsMember() throws Exception {
    login("member@example.com", "password123", memberSession);

    RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("viewer@example.com");

    mockMvc.perform(delete("/api/workspaces/{id}/members", workspaceId)
            .session(memberSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject remove owner")
  void shouldRejectRemoveOwner() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("owner@example.com");

    mockMvc.perform(delete("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Cannot delete owner"));

    assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, ownerId)).isTrue();
  }

  @Test
  @DisplayName("Should reject remove non-existent member")
  void shouldRejectRemoveNonExistentMember() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("nonexistent@example.com");

    mockMvc.perform(delete("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("User not found"));
  }

  @Test
  @DisplayName("Should reject remove with invalid email")
  void shouldRejectRemoveWithInvalidEmail() throws Exception {
    login("owner@example.com", "password123", ownerSession);

    RemoveWorkspaceMemeberRequest request = new RemoveWorkspaceMemeberRequest("invalid-email");

    mockMvc.perform(delete("/api/workspaces/{id}/members", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}