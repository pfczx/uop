package com.platform.uop.integrations.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;
import com.platform.uop.monitor.dto.UpdateMonitorRequest;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.MonitorService;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class MonitorServiceIT {

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
  private MonitorService monitorService;

  @Autowired
  private MonitorRepository monitorRepository;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @BeforeEach
  void setUp() {
    monitorRepository.deleteAll();
    workspaceRepository.deleteAll();
  }

  private Workspace createWorkspace() {
    return workspaceRepository.save(new Workspace("Test Workspace", "Test Description"));
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

  private Monitor createMonitor(Workspace workspace, boolean active) {
    return monitorRepository.save(new Monitor(workspace, descriptor("desc-1", active)));
  }

  @Test
  @DisplayName("Should create monitor and return response")
  void create_Success() {
    Workspace workspace = createWorkspace();
    UUID workspaceId = workspace.getId();
    CreateMonitorRequest request = new CreateMonitorRequest(descriptor("desc-1", false));

    MonitorResponse response = monitorService.create(request, workspaceId);

    assertThat(response.id()).isNotNull();
    assertThat(response.workspaceId()).isEqualTo(workspaceId);
    assertThat(response.description()).isEqualTo("A test monitor");

    assertThat(monitorRepository.findById(response.id())).isPresent();
  }

  @Test
  @DisplayName("Should throw NOT_FOUND when workspace does not exist on create")
  void create_WorkspaceNotFound_Throws() {
    assertThatThrownBy(() -> monitorService.create(
        new CreateMonitorRequest(descriptor("desc-1", false)), UUID.randomUUID()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Workspace not found");
  }

  @Test
  @DisplayName("Should update monitor descriptor and persist active flag")
  void update_Success() {
    Workspace workspace = createWorkspace();
    Monitor monitor = createMonitor(workspace, false);
    UUID monitorId = monitor.getId();
    UUID workspaceId = workspace.getId();

    MonitorResponse response = monitorService.update(
        new UpdateMonitorRequest(descriptor("desc-1", true)), workspaceId, monitorId);

    assertThat(response.description()).isEqualTo("A test monitor");
    assertThat(monitorRepository.findById(monitorId).get().getDescriptor().getActive())
        .isTrue();
  }

  @Test
  @DisplayName("Should set monitor active and persist")
  void setActive_Success() {
    Workspace workspace = createWorkspace();
    Monitor monitor = createMonitor(workspace, false);
    UUID workspaceId = workspace.getId();

    MonitorResponse response = monitorService.setActive(workspaceId, monitor.getId());

    assertThat(response.description()).isEqualTo("A test monitor");
    assertThat(monitorRepository.findById(monitor.getId()).get().getDescriptor().getActive())
        .isTrue();
  }

  @Test
  @DisplayName("Should set monitor inactive and persist")
  void setInactive_Success() {
    Workspace workspace = createWorkspace();
    Monitor monitor = createMonitor(workspace, true);
    UUID workspaceId = workspace.getId();

    MonitorResponse response = monitorService.setInactive(workspaceId, monitor.getId());

    assertThat(response.description()).isEqualTo("A test monitor");
    assertThat(monitorRepository.findById(monitor.getId()).get().getDescriptor().getActive())
        .isFalse();
  }

  @Test
  @DisplayName("Should remove monitor and delete from database")
  void remove_Success() {
    Workspace workspace = createWorkspace();
    Monitor monitor = createMonitor(workspace, false);
    UUID workspaceId = workspace.getId();
    UUID monitorId = monitor.getId();

    monitorService.remove(workspaceId, monitorId);

    assertThat(monitorRepository.findById(monitorId)).isEmpty();
  }

  @Test
  @DisplayName("Should generate installation command with registration token")
  void generateInstallationCommand_Success() {
    Workspace workspace = createWorkspace();
    Monitor monitor = createMonitor(workspace, false);
    UUID workspaceId = workspace.getId();

    String command = monitorService.generateInstallationCommand(workspaceId, monitor.getId());

    assertThat(command).contains("http://localhost:8081/agent/install");
    assertThat(command).contains("http://localhost:8080");
    assertThat(command).contains("--registration-token");
  }

  @Test
  @DisplayName("Should return monitor descriptor")
  void getDescriptor_Success() {
    Workspace workspace = createWorkspace();
    Monitor monitor = createMonitor(workspace, false);
    UUID workspaceId = workspace.getId();

    MonitorDescriptor descriptor = monitorService.getDescriptor(workspaceId, monitor.getId());

    assertThat(descriptor.getId()).isEqualTo("desc-1");
    assertThat(descriptor.getName()).isEqualTo("Test Monitor");
  }

  @Test
  @DisplayName("Should throw NOT_FOUND for operations on missing monitor")
  void operations_MonitorNotFound_Throw() {
    Workspace workspace = createWorkspace();
    UUID workspaceId = workspace.getId();
    UUID missing = UUID.randomUUID();

    assertThatThrownBy(() -> monitorService.update(
        new UpdateMonitorRequest(descriptor("desc-1", true)), workspaceId, missing))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Monitor not found");

    assertThatThrownBy(() -> monitorService.setActive(workspaceId, missing))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Monitor not found");

    assertThatThrownBy(() -> monitorService.setInactive(workspaceId, missing))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Monitor not found");

    assertThatThrownBy(() -> monitorService.remove(workspaceId, missing))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Monitor not found");

    assertThatThrownBy(() -> monitorService.generateInstallationCommand(workspaceId, missing))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Monitor not found");

    assertThatThrownBy(() -> monitorService.getDescriptor(workspaceId, missing))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Monitor not found");
  }

  @Test
  @DisplayName("Should throw HttpStatus NOT_FOUND for missing monitor")
  void missingMonitor_ReturnsNotFoundStatus() {
    UUID workspaceId = UUID.randomUUID();
    UUID monitorId = UUID.randomUUID();

    assertThatThrownBy(() -> monitorService.getDescriptor(workspaceId, monitorId))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND));
  }
}
