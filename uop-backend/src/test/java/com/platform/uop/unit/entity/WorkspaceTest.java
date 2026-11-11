package com.platform.uop.unit.entity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.platform.uop.workspace.entity.Workspace;

class WorkspaceTest {

  private static Workspace workspace() {
    return new Workspace("Test Workspace", "Test Description");
  }

  @Test
  @DisplayName("Should create workspace with correct fields")
  void createWorkspace_SetsFields() {
    Workspace workspace = workspace();

    assertThat(workspace.getName()).isEqualTo("Test Workspace");
    assertThat(workspace.getDescription()).isEqualTo("Test Description");
    assertThat(workspace.getId()).isNull();
    assertThat(workspace.getCreatedAt()).isNull();
    assertThat(workspace.getUpdatedAt()).isNull();
    assertThat(workspace.getMembers()).isEmpty();
  }

  @Test
  @DisplayName("Should update workspace name and description")
  void update_UpdatesFields() {
    Workspace workspace = workspace();
    Instant beforeUpdatedAt = Instant.now();

    workspace.update("Updated Name", "Updated Description");

    assertThat(workspace.getName()).isEqualTo("Updated Name");
    assertThat(workspace.getDescription()).isEqualTo("Updated Description");
  }
}