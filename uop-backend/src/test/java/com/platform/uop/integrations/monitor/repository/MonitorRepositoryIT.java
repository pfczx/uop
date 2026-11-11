package com.platform.uop.integrations.monitor.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class MonitorRepositoryIT {

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
  private MonitorRepository monitorRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @BeforeEach
  void setUp() {
    monitorRepository.deleteAll();
    workspaceRepository.deleteAll();
  }

  private static MonitorDescriptor descriptor(String id, boolean active) {
    return MonitorDescriptor.newBuilder()
        .setId(id)
        .setName("Test Monitor")
        .setDescription("A test monitor")
        .setVersion("1.0")
        .setActive(active)
        .build();
  }

  @Test
  @DisplayName("Should save and find monitor with descriptor round-tripped through bytea")
  void shouldSaveAndFindMonitorWithDescriptor() {
    Workspace workspace = workspaceRepository.save(new Workspace("Test Workspace", "desc"));
    Monitor monitor = new Monitor(workspace, descriptor("desc-1", false));
    Monitor saved = monitorRepository.save(monitor);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();

    Optional<Monitor> found = monitorRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getWorkspace().getId()).isEqualTo(workspace.getId());
    assertThat(found.get().getDescriptor().getId()).isEqualTo("desc-1");
    assertThat(found.get().getDescriptor().getName()).isEqualTo("Test Monitor");
    assertThat(found.get().getDescriptor().getDescription()).isEqualTo("A test monitor");
    assertThat(found.get().getDescriptor().getVersion()).isEqualTo("1.0");
    assertThat(found.get().getDescriptor().getActive()).isFalse();
  }

  @Test
  @DisplayName("Should update descriptor in place")
  void shouldUpdateDescriptor() {
    Workspace workspace = workspaceRepository.save(new Workspace("Test Workspace", "desc"));
    Monitor monitor = monitorRepository.save(new Monitor(workspace, descriptor("desc-1", false)));

    MonitorDescriptor updated = descriptor("desc-1", true);
    monitor.updateDescriptor(updated);
    Monitor saved = monitorRepository.save(monitor);

    Optional<Monitor> found = monitorRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getDescriptor().getActive()).isTrue();
    assertThat(found.get().getDescriptor().getId()).isEqualTo("desc-1");
  }

  @Test
  @DisplayName("Should delete monitor")
  void shouldDeleteMonitor() {
    Workspace workspace = workspaceRepository.save(new Workspace("Test Workspace", "desc"));
    Monitor monitor = monitorRepository.save(new Monitor(workspace, descriptor("desc-1", false)));

    monitorRepository.delete(monitor);

    assertThat(monitorRepository.findById(monitor.getId())).isEmpty();
  }
}
