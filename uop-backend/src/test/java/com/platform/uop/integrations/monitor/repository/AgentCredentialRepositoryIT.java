package com.platform.uop.integrations.monitor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.platform.uop.monitor.entity.AgentCredential;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.AgentCredentialRepository;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AgentCredentialRepositoryIT {

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
  private AgentCredentialRepository credentialRepository;

  @Autowired
  private MonitorRepository monitorRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @BeforeEach
  void setUp() {
    credentialRepository.deleteAll();
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
  @DisplayName("Should save and find agent credential by monitor id")
  void shouldSaveAndFindByMonitorId() {
    Monitor monitor = createMonitor();
    AgentCredential credential = new AgentCredential(monitor);
    credential.setRegistrationToken("token-hash", Instant.now().plusSeconds(60));
    AgentCredential saved = credentialRepository.save(credential);

    assertThat(saved.getId()).isNotNull();

    Optional<AgentCredential> found = credentialRepository.findByMonitor_Id(monitor.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getRegistrationTokenHash()).isEqualTo("token-hash");
  }

  @Test
  @DisplayName("Should find agent credential by credential hash")
  void shouldFindByCredentialHash() {
    Monitor monitor = createMonitor();
    AgentCredential credential = new AgentCredential(monitor);
    credential.setCredential("credential-hash");
    credentialRepository.save(credential);

    Optional<AgentCredential> found = credentialRepository.findByCredentialHash("credential-hash");

    assertThat(found).isPresent();
    assertThat(found.get().getMonitor().getId()).isEqualTo(monitor.getId());
  }

  @Test
  @Transactional
  @DisplayName("Should find agent credential by registration token hash for update")
  void shouldFindByRegistrationTokenHashForUpdate() {
    Monitor monitor = createMonitor();
    AgentCredential credential = new AgentCredential(monitor);
    credential.setRegistrationToken("token-hash", Instant.now().plusSeconds(60));
    credentialRepository.save(credential);

    Optional<AgentCredential> found = credentialRepository
        .findByRegistrationTokenHashForUpdate("token-hash");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(credential.getId());
  }

  @Test
  @Transactional
  @DisplayName("Should return empty when registration token hash does not exist")
  void shouldReturnEmptyWhenTokenHashNotFound() {
    Optional<AgentCredential> found = credentialRepository
        .findByRegistrationTokenHashForUpdate("unknown");

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("Should return empty when monitor id has no credential")
  void shouldReturnEmptyWhenMonitorHasNoCredential() {
    Monitor monitor = createMonitor();

    Optional<AgentCredential> found = credentialRepository.findByMonitor_Id(monitor.getId());

    assertThat(found).isEmpty();
  }
}
