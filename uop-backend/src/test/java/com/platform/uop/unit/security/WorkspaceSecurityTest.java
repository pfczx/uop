package com.platform.uop.unit.security;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.users.entity.User;
import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import com.platform.uop.workspace.security.WorkspaceSecurity;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class WorkspaceSecurityTest {

  @Mock
  private WorkspaceMemberRepository workspaceMemberRepository;

  @InjectMocks
  private WorkspaceSecurity workspaceSecurity;

  private static User user(UUID id) {
    return User.builder()
        .id(id)
        .email("user@example.com")
        .passwordHash("hash")
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .createdAt(Instant.now().minusSeconds(60))
        // .updatedAt(Instant.now().minusSeconds(60))
        .build();
  }

  private static UserPrincipal principal(UUID userId) {
    return new UserPrincipal(user(userId));
  }

  private static Authentication authentication(UUID userId) {
    return new UsernamePasswordAuthenticationToken(principal(userId), null, List.of());
  }

  @Nested
  @DisplayName("isMember")
  class IsMember {

    @Test
    @DisplayName("Should return true when user is member of workspace")
    void isMember_True() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      Authentication auth = authentication(userId);

      when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
          .thenReturn(true);

      boolean result = workspaceSecurity.isMember(workspaceId, auth);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when user is not member of workspace")
    void isMember_False() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      Authentication auth = authentication(userId);

      when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId))
          .thenReturn(false);

      boolean result = workspaceSecurity.isMember(workspaceId, auth);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when authentication is null")
    void isMember_NullAuth_ReturnsFalse() {
      UUID workspaceId = UUID.randomUUID();

      boolean result = workspaceSecurity.isMember(workspaceId, (Authentication) null);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when authentication is not authenticated")
    void isMember_NotAuthenticated_ReturnsFalse() {
      UUID workspaceId = UUID.randomUUID();
      Authentication auth = new UsernamePasswordAuthenticationToken("principal", "credentials");
      auth.setAuthenticated(false);

      boolean result = workspaceSecurity.isMember(workspaceId, auth);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when principal is not UserPrincipal")
    void isMember_InvalidPrincipal_ReturnsFalse() {
      UUID workspaceId = UUID.randomUUID();
      Authentication auth = new UsernamePasswordAuthenticationToken("string-principal", "credentials");

      boolean result = workspaceSecurity.isMember(workspaceId, auth);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("canManageMembers")
  class CanManageMembers {

    @Test
    @DisplayName("Should return true when user is OWNER")
    void canManageMembers_Owner_True() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      Authentication auth = authentication(userId);

      when(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndRoleIn(
          workspaceId, userId, List.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN)))
          .thenReturn(true);

      boolean result = workspaceSecurity.canManageMembers(workspaceId, auth);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return true when user is ADMIN")
    void canManageMembers_Admin_True() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      Authentication auth = authentication(userId);

      when(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndRoleIn(
          workspaceId, userId, List.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN)))
          .thenReturn(true);

      boolean result = workspaceSecurity.canManageMembers(workspaceId, auth);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when user is MEMBER")
    void canManageMembers_Member_False() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      Authentication auth = authentication(userId);

      when(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndRoleIn(
          workspaceId, userId, List.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN)))
          .thenReturn(false);

      boolean result = workspaceSecurity.canManageMembers(workspaceId, auth);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when authentication is null")
    void canManageMembers_NullAuth_ReturnsFalse() {
      UUID workspaceId = UUID.randomUUID();

      boolean result = workspaceSecurity.canManageMembers(workspaceId, (Authentication) null);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("isOwner")
  class IsOwner {

    @Test
    @DisplayName("Should return true when user is OWNER")
    void isOwner_True() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      Authentication auth = authentication(userId);

      when(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndRole(
          workspaceId, userId, WorkspaceRole.OWNER))
          .thenReturn(true);

      boolean result = workspaceSecurity.isOwner(workspaceId, auth);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when user is ADMIN")
    void isOwner_Admin_False() {
      UUID workspaceId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      Authentication auth = authentication(userId);

      when(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndRole(
          workspaceId, userId, WorkspaceRole.OWNER))
          .thenReturn(false);

      boolean result = workspaceSecurity.isOwner(workspaceId, auth);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when authentication is null")
    void isOwner_NullAuth_ReturnsFalse() {
      UUID workspaceId = UUID.randomUUID();

      boolean result = workspaceSecurity.isOwner(workspaceId, (Authentication) null);

      assertThat(result).isFalse();
    }
  }
}
