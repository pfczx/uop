package com.platform.uop.integrations.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AgentCredentialServiceIT {

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
  private AgentCredentialService agentCredentialService;

  @Autowired
  private MonitorRepository monitorRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @BeforeEach
  void setUp() {
    monitorRepository.deleteAll();
    workspaceRepository.deleteAll();
  }

  private static MonitorDescriptor descriptor(String id) {
    return MonitorDescriptor.newBuilder()
        .setId(id)
        .setName("Test Monitor")
        .build();
  }

  private Monitor createMonitor() {
    Workspace workspace = workspaceRepository.save(new Workspace("Test Workspace", "desc"));
    return monitorRepository.save(new Monitor(workspace, descriptor("desc-1")));
  }

  @Test
  @DisplayName("Should create registration token for existing monitor")
  void createRegistrationToken_Success() {
    Monitor monitor = createMonitor();

    String token = agentCredentialService.createRegistrationToken(monitor.getId());

    assertThat(token).isNotNull();
    assertThat(token).isNotEmpty();
  }

  @Test
  @DisplayName("Should throw NOT_FOUND when creating token for missing monitor")
  void createRegistrationToken_MonitorNotFound_Throws() {
    assertThatThrownBy(() -> agentCredentialService.createRegistrationToken(UUID.randomUUID()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Monitor not found");
  }

  @Test
  @DisplayName("Should register agent and issue credential from valid token")
  void registerAgent_Success() {
    Monitor monitor = createMonitor();
    String token = agentCredentialService.createRegistrationToken(monitor.getId());

    AgentCredentialService.RegisteredAgent registered = agentCredentialService.registerAgent(token);

    assertThat(registered.monitorId()).isEqualTo(monitor.getId());
    assertThat(registered.credential()).isNotNull();
    assertThat(registered.credential()).isNotEqualTo(token);
  }

  @Test
  @DisplayName("Should throw UNAUTHORIZED for unknown registration token")
  void registerAgent_UnknownToken_Throws() {
    assertThatThrownBy(() -> agentCredentialService.registerAgent("unknown-token"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid registration token");
  }

  @Test
  @DisplayName("Should authenticate agent with issued credential")
  void authenticate_Success() {
    Monitor monitor = createMonitor();
    String token = agentCredentialService.createRegistrationToken(monitor.getId());
    AgentCredentialService.RegisteredAgent registered = agentCredentialService.registerAgent(token);

    UUID monitorId = agentCredentialService.authenticate(registered.credential());

    assertThat(monitorId).isEqualTo(monitor.getId());
  }

  @Test
  @DisplayName("Should throw UNAUTHORIZED for unknown credential")
  void authenticate_UnknownCredential_Throws() {
    assertThatThrownBy(() -> agentCredentialService.authenticate("unknown-credential"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid agent credential");
  }

  @Test
  @DisplayName("Should throw HttpStatus UNAUTHORIZED for invalid credential")
  void authenticate_InvalidCredential_ReturnsUnauthorized() {
    assertThatThrownBy(() -> agentCredentialService.authenticate("bad"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED));
  }

  @Test
  @DisplayName("Should revoke agent credentials for monitor")
  void revokeAgentCredentials_Success() {
    Monitor monitor = createMonitor();
    String token = agentCredentialService.createRegistrationToken(monitor.getId());
    agentCredentialService.registerAgent(token);

    agentCredentialService.revokeAgentCredentials(monitor.getId());

    assertThatThrownBy(() -> agentCredentialService.authenticate(
        agentCredentialService.createRegistrationToken(monitor.getId())))
        .isInstanceOf(ResponseStatusException.class);
  }
}
