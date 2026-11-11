package com.platform.uop.unit.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;
import com.platform.uop.monitor.dto.RemoveMonitorRequest;
import com.platform.uop.monitor.dto.SetActiveRequest;
import com.platform.uop.monitor.dto.SetInactiveRequest;
import com.platform.uop.monitor.dto.UpdateMonitorRequest;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.monitor.service.AgentConnectionService;
import com.platform.uop.monitor.service.AgentCredentialService;
import com.platform.uop.monitor.service.MonitorService;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

  @Mock
  private MonitorRepository monitorRepository;

  @Mock
  private WorkspaceRepository workspaceRepository;

  @Mock
  private AgentCredentialService agentCredentialService;

  @Mock
  private AgentConnectionService agentConnectionService;

  @InjectMocks
  private MonitorService monitorService;

  private static Workspace workspace(UUID id) {
    Workspace ws = new Workspace("Test Workspace", "Test Description");
    try {
      java.lang.reflect.Field field = Workspace.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(ws, id);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    return ws;
  }

  private static Monitor monitor(UUID id, Workspace workspace, boolean active) {
    Monitor m = new Monitor(workspace, descriptor(active));
    try {
      java.lang.reflect.Field field = Monitor.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(m, id);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    return m;
  }

  private static MonitorDescriptor descriptor(boolean active) {
    return MonitorDescriptor.newBuilder()
        .setId("desc-1")
        .setName("Test Monitor")
        .setDescription("A test monitor")
        .setActive(active)
        .build();
  }

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(monitorService, "installerUrl",
        "http://localhost:8081/agent/install");
    ReflectionTestUtils.setField(monitorService, "platformUrl",
        "http://localhost:8080");
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("Should create monitor and return response")
    void create_Success() {
      UUID workspaceId = UUID.randomUUID();
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(workspaceId);
      MonitorDescriptor descriptor = descriptor(false);
      CreateMonitorRequest request = new CreateMonitorRequest(descriptor);
      Monitor saved = monitor(monitorId, workspace, false);

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
      when(monitorRepository.save(any(Monitor.class))).thenReturn(saved);

      MonitorResponse response = monitorService.create(request, workspaceId);

      assertThat(response.id()).isEqualTo(monitorId);
      assertThat(response.workspaceId()).isEqualTo(workspaceId);
      assertThat(response.desription()).isEqualTo("A test monitor");

      verify(workspaceRepository).findById(workspaceId);
      verify(monitorRepository).save(any(Monitor.class));
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when workspace does not exist")
    void create_WorkspaceNotFound_Throws() {
      UUID workspaceId = UUID.randomUUID();
      CreateMonitorRequest request = new CreateMonitorRequest(descriptor(false));

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.create(request, workspaceId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Workspace not found");

      verify(monitorRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("Should update descriptor and synchronize after commit")
    void update_Success() {
      UUID workspaceId = UUID.randomUUID();
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(workspaceId);
      Monitor monitor = monitor(monitorId, workspace, false);
      MonitorDescriptor updatedDescriptor = descriptor(true);
      UpdateMonitorRequest request = new UpdateMonitorRequest(updatedDescriptor);

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

      MonitorResponse response = monitorService.update(request, workspaceId, monitorId);

      assertThat(monitor.getDescriptor().getActive()).isTrue();
      assertThat(response.desription()).isEqualTo("A test monitor");
      verify(monitorRepository).save(monitor);
      verify(agentConnectionService).sendDescriptor(eq(monitorId), eq(updatedDescriptor));
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when workspace does not exist")
    void update_WorkspaceNotFound_Throws() {
      UUID workspaceId = UUID.randomUUID();
      UUID monitorId = UUID.randomUUID();
      UpdateMonitorRequest request = new UpdateMonitorRequest(descriptor(true));

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.update(request, workspaceId, monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Workspace not found");

      verify(monitorRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when monitor does not exist")
    void update_MonitorNotFound_Throws() {
      UUID workspaceId = UUID.randomUUID();
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(workspaceId);
      UpdateMonitorRequest request = new UpdateMonitorRequest(descriptor(true));

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
      when(monitorRepository.findById(monitorId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.update(request, workspaceId, monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Monitor not found");

      verify(monitorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when descriptor id changes")
    void update_DescriptorIdChanged_Throws() {
      UUID workspaceId = UUID.randomUUID();
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(workspaceId);
      Monitor monitor = monitor(monitorId, workspace, false);
      MonitorDescriptor differentId = MonitorDescriptor.newBuilder()
          .setId("desc-2")
          .setName("Test Monitor")
          .build();
      UpdateMonitorRequest request = new UpdateMonitorRequest(differentId);

      when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

      assertThatThrownBy(() -> monitorService.update(request, workspaceId, monitorId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Monitor descriptor ID cannot be changed");

      verify(monitorRepository, never()).save(any());
      verify(agentConnectionService, never()).sendDescriptor(any(), any());
    }
  }

  @Nested
  @DisplayName("remove")
  class Remove {

    @Test
    @DisplayName("Should remove monitor and revoke credentials after commit")
    void remove_Success() {
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(UUID.randomUUID());
      Monitor monitor = monitor(monitorId, workspace, false);

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

      monitorService.remove(new RemoveMonitorRequest(), monitorId);

      verify(monitorRepository).delete(monitor);
      verify(agentConnectionService).closeMonitorConnection(monitorId);
      verify(agentCredentialService).revokeAgentCredentials(monitorId);
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when monitor does not exist")
    void remove_MonitorNotFound_Throws() {
      UUID monitorId = UUID.randomUUID();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.remove(new RemoveMonitorRequest(), monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Monitor not found");

      verify(monitorRepository, never()).delete(any());
      verify(agentConnectionService, never()).closeMonitorConnection(any());
      verify(agentCredentialService, never()).revokeAgentCredentials(any());
    }
  }

  @Nested
  @DisplayName("setActive")
  class SetActive {

    @Test
    @DisplayName("Should set monitor active and synchronize descriptor")
    void setActive_Success() {
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(UUID.randomUUID());
      Monitor monitor = monitor(monitorId, workspace, false);
      MonitorDescriptor updated = descriptor(true);
      SetActiveRequest request = new SetActiveRequest();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

      MonitorResponse response = monitorService.setActive(request, monitorId);

      assertThat(monitor.getDescriptor().getActive()).isTrue();
      assertThat(response.desription()).isEqualTo("A test monitor");
      verify(monitorRepository).save(monitor);
      verify(agentConnectionService).sendDescriptor(eq(monitorId), eq(updated));
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when monitor does not exist")
    void setActive_MonitorNotFound_Throws() {
      UUID monitorId = UUID.randomUUID();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.setActive(new SetActiveRequest(), monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Monitor not found");

      verify(monitorRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("setInactive")
  class SetInactive {

    @Test
    @DisplayName("Should set monitor inactive and synchronize descriptor")
    void setInactive_Success() {
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(UUID.randomUUID());
      Monitor monitor = monitor(monitorId, workspace, true);
      MonitorDescriptor updated = descriptor(false);
      SetInactiveRequest request = new SetInactiveRequest();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

      MonitorResponse response = monitorService.setInactive(request, monitorId);

      assertThat(monitor.getDescriptor().getActive()).isFalse();
      assertThat(response.desription()).isEqualTo("A test monitor");
      verify(monitorRepository).save(monitor);
      verify(agentConnectionService).sendDescriptor(eq(monitorId), eq(updated));
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when monitor does not exist")
    void setInactive_MonitorNotFound_Throws() {
      UUID monitorId = UUID.randomUUID();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.setInactive(new SetInactiveRequest(), monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Monitor not found");

      verify(monitorRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("generateInstallationCommand")
  class GenerateInstallationCommand {

    @Test
    @DisplayName("Should return installation command with registration token")
    void generateInstallationCommand_Success() {
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(UUID.randomUUID());
      Monitor monitor = monitor(monitorId, workspace, false);
      String token = "registration-token";

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));
      when(agentCredentialService.createRegistrationToken(monitorId)).thenReturn(token);

      String command = monitorService.generateInstallationCommand(monitorId);

      assertThat(command).contains("http://localhost:8081/agent/install");
      assertThat(command).contains("http://localhost:8080");
      assertThat(command).contains("'registration-token'");
      verify(agentCredentialService).createRegistrationToken(monitorId);
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when monitor does not exist")
    void generateInstallationCommand_MonitorNotFound_Throws() {
      UUID monitorId = UUID.randomUUID();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.generateInstallationCommand(monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Monitor not found");

      verify(agentCredentialService, never()).createRegistrationToken(any());
    }
  }

  @Nested
  @DisplayName("getDescriptor")
  class GetDescriptor {

    @Test
    @DisplayName("Should return monitor descriptor")
    void getDescriptor_Success() {
      UUID monitorId = UUID.randomUUID();
      Workspace workspace = workspace(UUID.randomUUID());
      Monitor monitor = monitor(monitorId, workspace, false);

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.of(monitor));

      MonitorDescriptor descriptor = monitorService.getDescriptor(monitorId);

      assertThat(descriptor.getId()).isEqualTo("desc-1");
      assertThat(descriptor.getName()).isEqualTo("Test Monitor");
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when monitor does not exist")
    void getDescriptor_MonitorNotFound_Throws() {
      UUID monitorId = UUID.randomUUID();

      when(monitorRepository.findById(monitorId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> monitorService.getDescriptor(monitorId))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Monitor not found");
    }
  }
}
