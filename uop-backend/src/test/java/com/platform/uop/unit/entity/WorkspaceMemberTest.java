package com.platform.uop.unit.entity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;

class WorkspaceMemberTest {

  private static Workspace workspace() {
    return new Workspace("Test Workspace", "Test Description");
  }

  private static User user() {
    return User.builder()
        .id(UUID.randomUUID())
        .email("user@example.com")
        .passwordHash("hash")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now().minusSeconds(60))
        .updatedAt(Instant.now().minusSeconds(60))
        .build();
  }

  @Test
  @DisplayName("Should create workspace member with correct fields")
  void createWorkspaceMember_SetsFields() {
    Workspace workspace = workspace();
    User user = user();

    WorkspaceMember member = new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER);

    assertThat(member.getWorkspace()).isEqualTo(workspace);
    assertThat(member.getUser()).isEqualTo(user);
    assertThat(member.getRole()).isEqualTo(WorkspaceRole.MEMBER);
    assertThat(member.getId()).isNull();
    assertThat(member.getCreatedAt()).isNull();
  }

  @Test
  @DisplayName("Should change role and keep other fields")
  void changeRole_UpdatesRole() {
    Workspace workspace = workspace();
    User user = user();

    WorkspaceMember member = new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER);

    member.changeRole(WorkspaceRole.ADMIN);

    assertThat(member.getRole()).isEqualTo(WorkspaceRole.ADMIN);
    assertThat(member.getWorkspace()).isEqualTo(workspace);
    assertThat(member.getUser()).isEqualTo(user);
  }

  @Test
  @DisplayName("Should allow changing to all valid roles")
  void changeRole_AllRoles() {
    Workspace workspace = workspace();
    User user = user();

    WorkspaceMember member = new WorkspaceMember(workspace, user, WorkspaceRole.VIEWER);

    member.changeRole(WorkspaceRole.MEMBER);
    assertThat(member.getRole()).isEqualTo(WorkspaceRole.MEMBER);

    member.changeRole(WorkspaceRole.ADMIN);
    assertThat(member.getRole()).isEqualTo(WorkspaceRole.ADMIN);

    member.changeRole(WorkspaceRole.OWNER);
    assertThat(member.getRole()).isEqualTo(WorkspaceRole.OWNER);
  }
}