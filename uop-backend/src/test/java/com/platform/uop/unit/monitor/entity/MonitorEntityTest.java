package com.platform.uop.unit.monitor.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;

class MonitorEntityTest {

  private static Workspace workspace(UUID id) {
    Workspace ws = new Workspace("Test Workspace", "Test Description");
    try {
      Field field = Workspace.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(ws, id);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    return ws;
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
  @DisplayName("Should create monitor with workspace and descriptor")
  void create_SetsFields() {
    Workspace workspace = workspace(UUID.randomUUID());
    MonitorDescriptor descriptor = descriptor("desc-1", false);

    Monitor monitor = new Monitor(workspace, descriptor);

    assertThat(monitor.getWorkspace()).isEqualTo(workspace);
    assertThat(monitor.getDescriptor()).isEqualTo(descriptor);
    assertThat(monitor.getId()).isNull();
    assertThat(monitor.getCreatedAt()).isNull();
    assertThat(monitor.getUpdatedAt()).isNull();
  }

  @Test
  @DisplayName("Should update descriptor when id is unchanged")
  void updateDescriptor_SameId_Updates() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = new Monitor(workspace, descriptor("desc-1", false));

    MonitorDescriptor updated = descriptor("desc-1", true);
    monitor.updateDescriptor(updated);

    assertThat(monitor.getDescriptor()).isEqualTo(updated);
    assertThat(monitor.getDescriptor().getActive()).isTrue();
  }

  @Test
  @DisplayName("Should throw NullPointerException when descriptor is null")
  void updateDescriptor_NullDescriptor_Throws() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = new Monitor(workspace, descriptor("desc-1", false));

    assertThatThrownBy(() -> monitor.updateDescriptor(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("descriptor must not be null");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when descriptor id changes")
  void updateDescriptor_DifferentId_Throws() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = new Monitor(workspace, descriptor("desc-1", false));

    assertThatThrownBy(() -> monitor.updateDescriptor(descriptor("desc-2", true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Monitor descriptor ID cannot be changed");
  }

  @Test
  @DisplayName("Should set monitor active")
  void setActive_SetsActiveTrue() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = new Monitor(workspace, descriptor("desc-1", false));

    monitor.setActive();

    assertThat(monitor.getDescriptor().getActive()).isTrue();
  }

  @Test
  @DisplayName("Should set monitor inactive")
  void setInactive_SetsActiveFalse() {
    Workspace workspace = workspace(UUID.randomUUID());
    Monitor monitor = new Monitor(workspace, descriptor("desc-1", true));

    monitor.setInactive();

    assertThat(monitor.getDescriptor().getActive()).isFalse();
  }
}
