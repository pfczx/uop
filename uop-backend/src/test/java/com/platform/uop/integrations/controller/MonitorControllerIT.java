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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import com.platform.uop.auth.dto.LoginRequest;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.users.repository.UserRepository;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import org.springframework.mock.web.MockHttpSession;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitorControllerIT {

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
  private MonitorRepository monitorRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private UUID workspaceId;
  private UUID ownerId;
  private UUID adminId;
  private UUID memberId;
  private UUID viewerId;
  private UUID monitorId;
  private MockHttpSession ownerSession;
  private MockHttpSession adminSession;
  private MockHttpSession memberSession;
  private MockHttpSession viewerSession;
  private MockHttpSession unauthenticatedSession;

  @BeforeEach
  void setUp() throws Exception {
    monitorRepository.deleteAll();
    workspaceMemberRepository.deleteAll();
    workspaceRepository.deleteAll();
    userRepository.deleteAll();

    ownerId = createUser("owner@example.com", "password123", UserRole.USER, UserStatus.ACTIVE);
    adminId = createUser("admin@example.com", "password123", UserRole.USER, UserStatus.ACTIVE);
    memberId = createUser("member@example.com", "password123", UserRole.USER, UserStatus.ACTIVE);
    viewerId = createUser("viewer@example.com", "password123", UserRole.USER, UserStatus.ACTIVE);

    ownerSession = new MockHttpSession();
    adminSession = new MockHttpSession();
    memberSession = new MockHttpSession();
    viewerSession = new MockHttpSession();
    unauthenticatedSession = new MockHttpSession();

    login("owner@example.com", "password123", ownerSession);
    login("admin@example.com", "password123", adminSession);
    login("member@example.com", "password123", memberSession);
    login("viewer@example.com", "password123", viewerSession);

    Workspace workspace = workspaceRepository.save(new Workspace("Test Workspace", "Test Description"));
    workspaceId = workspace.getId();

    workspaceMemberRepository.saveAll(java.util.List.of(
        new WorkspaceMember(workspace, userRepository.findByEmail("owner@example.com").orElseThrow(),
            WorkspaceRole.OWNER),
        new WorkspaceMember(workspace, userRepository.findByEmail("admin@example.com").orElseThrow(),
            WorkspaceRole.ADMIN),
        new WorkspaceMember(workspace, userRepository.findByEmail("member@example.com").orElseThrow(),
            WorkspaceRole.MEMBER),
        new WorkspaceMember(workspace, userRepository.findByEmail("viewer@example.com").orElseThrow(),
            WorkspaceRole.VIEWER)));

    Workspace ws = workspaceRepository.findById(workspaceId).orElseThrow();
    Monitor monitor = monitorRepository.save(new Monitor(ws, descriptor("desc-1", true)));
    monitorId = monitor.getId();
  }

  private UUID createUser(String email, String password, UserRole role, UserStatus status) {
    User user = User.builder()
        .id(UUID.randomUUID())
        .email(email)
        .passwordHash(passwordEncoder.encode(password))
        .role(role)
        .status(status)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    return userRepository.save(user).getId();
  }

  private void login(String email, String password, MockHttpSession session) throws Exception {
    mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginRequest(email, password)))
            .session(session))
        .andExpect(status().isOk());
  }

  private static MonitorDescriptor descriptor(String id, boolean active) {
    return MonitorDescriptor.newBuilder()
        .setId(id)
        .setName("Test Monitor")
        .setDescription("A test monitor")
        .setVersion("1.0.0")
        .setActive(active)
        .build();
  }

  private static String createRequestJson(boolean active) {
    return "{\"descriptor\":{\"id\":\"desc-new\",\"name\":\"New Monitor\","
        + "\"description\":\"A new monitor\",\"version\":\"1.0\",\"active\":" + active + "}}";
  }

  private static String updateRequestJson(boolean active) {
    return "{\"descriptor\":{\"id\":\"desc-1\",\"name\":\"Test Monitor\","
        + "\"description\":\"Updated monitor\",\"version\":\"1.0\",\"active\":" + active + "}}";
  }

  @Test
  @DisplayName("Should create monitor as workspace member")
  void createMonitor_Success() throws Exception {
    mockMvc.perform(post("/api/workspaces/{workspaceId}/monitors", workspaceId)
            .session(ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(false)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()));
  }

  @Test
  @DisplayName("Should reject create monitor without authentication")
  void createMonitor_Unauthenticated_Rejected() throws Exception {
    mockMvc.perform(post("/api/workspaces/{workspaceId}/monitors", workspaceId)
            .session(unauthenticatedSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(false)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Should reject create monitor for non-member workspace")
  void createMonitor_NonMember_Rejected() throws Exception {
    Workspace otherWs = workspaceRepository.save(new Workspace("Other Workspace", "desc"));

    mockMvc.perform(post("/api/workspaces/{workspaceId}/monitors", otherWs.getId())
            .session(memberSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequestJson(false)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should reject update monitor for non-member workspace")
  void updateMonitor_NonMember_Rejected() throws Exception {
    Workspace otherWs = workspaceRepository.save(new Workspace("Other Workspace", "desc"));

    mockMvc.perform(put("/api/workspaces/{workspaceId}/monitors/{monitorId}", otherWs.getId(), monitorId)
            .session(memberSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateRequestJson(false)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should remove monitor")
  void removeMonitor_Success() throws Exception {
    UUID newMonitorId = monitorRepository.save(new Monitor(
        workspaceRepository.findById(workspaceId).orElseThrow(),
        descriptor("desc-remove", true))).getId();

    mockMvc.perform(delete("/api/workspaces/{workspaceId}/monitors/{monitorId}", workspaceId, newMonitorId)
            .session(ownerSession))
        .andExpect(status().isNoContent());

    assertThat(monitorRepository.findById(newMonitorId)).isEmpty();
  }

  @Test
  @DisplayName("Should remove monitor as admin")
  void removeMonitor_AsAdmin_Success() throws Exception {
    UUID newMonitorId = monitorRepository.save(new Monitor(
        workspaceRepository.findById(workspaceId).orElseThrow(),
        descriptor("desc-remove-admin", true))).getId();

    mockMvc.perform(delete("/api/workspaces/{workspaceId}/monitors/{monitorId}", workspaceId, newMonitorId)
            .session(adminSession))
        .andExpect(status().isNoContent());

    assertThat(monitorRepository.findById(newMonitorId)).isEmpty();
  }

  @Test
  @DisplayName("Should reject remove monitor as non-member")
  void removeMonitor_NonMember_Rejected() throws Exception {
    mockMvc.perform(delete("/api/workspaces/{workspaceId}/monitors/{monitorId}", workspaceId, monitorId)
            .session(memberSession))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should activate monitor")
  void activateMonitor_Success() throws Exception {
    UUID inactiveMonitorId = monitorRepository.save(new Monitor(
        workspaceRepository.findById(workspaceId).orElseThrow(),
        descriptor("desc-activate", false))).getId();

    mockMvc.perform(patch("/api/workspaces/{workspaceId}/monitors/{monitorId}/active", workspaceId, inactiveMonitorId)
            .session(ownerSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("A test monitor"));
  }

  @Test
  @DisplayName("Should deactivate monitor")
  void deactivateMonitor_Success() throws Exception {
    mockMvc.perform(patch("/api/workspaces/{workspaceId}/monitors/{monitorId}/inactive", workspaceId, monitorId)
            .session(ownerSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("A test monitor"));
  }

  @Test
  @DisplayName("Should reject activate/deactivate non-existent monitor")
  void activateMonitor_NotFound_Rejected() throws Exception {
    UUID nonExistentId = UUID.randomUUID();

    mockMvc.perform(patch("/api/workspaces/{workspaceId}/monitors/{monitorId}/active", workspaceId, nonExistentId)
            .session(ownerSession))
        .andExpect(status().isNotFound());

    mockMvc.perform(patch("/api/workspaces/{workspaceId}/monitors/{monitorId}/inactive", workspaceId, nonExistentId)
            .session(ownerSession))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Should return install command")
  void getInstallCommand_Success() throws Exception {
    mockMvc.perform(post("/api/workspaces/{workspaceId}/monitors/{monitorId}/installation-command", workspaceId, monitorId)
            .session(ownerSession))
        .andExpect(status().isOk())
        .andExpect(result -> {
          String body = result.getResponse().getContentAsString();
          assertThat(body).contains("--registration-token");
        });
  }

  @Test
  @DisplayName("Should reject install command without authentication")
  void installCommand_Unauthenticated_Rejected() throws Exception {
    mockMvc.perform(post("/api/workspaces/{workspaceId}/monitors/{monitorId}/installation-command", workspaceId, monitorId)
            .session(unauthenticatedSession))
        .andExpect(status().isUnauthorized());
  }
}
