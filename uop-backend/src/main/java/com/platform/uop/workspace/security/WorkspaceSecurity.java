package com.platform.uop.workspace.security;

import com.platform.uop.auth.security.UserPrincipal;
import com.platform.uop.workspace.enums.WorkspaceRole;
import com.platform.uop.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("workspaceSecurity")
@RequiredArgsConstructor
public class WorkspaceSecurity {

  private final WorkspaceMemberRepository workspaceMemberRepository;

  public boolean isMember(UUID workspaceId, Authentication authentication) {
    UUID userId = getUserId(authentication);

    return userId != null
        && workspaceMemberRepository.existsByWorkspaceIdAndUserId(
            workspaceId, userId);
  }

  public boolean canManageMembers(
      UUID workspaceId,
      Authentication authentication) {
    UUID userId = getUserId(authentication);

    return userId != null
        && workspaceMemberRepository
            .existsByWorkspaceIdAndUserIdAndRoleIn(
                workspaceId,
                userId,
                List.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN));
  }

  public boolean isOwner(
      UUID workspaceId,
      Authentication authentication) {
    UUID userId = getUserId(authentication);

    return userId != null
        && workspaceMemberRepository
            .existsByWorkspaceIdAndUserIdAndRole(
                workspaceId, userId, WorkspaceRole.OWNER);
  }

  private UUID getUserId(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      return null;
    }

    return principal.getUser().getId();
  }
}
