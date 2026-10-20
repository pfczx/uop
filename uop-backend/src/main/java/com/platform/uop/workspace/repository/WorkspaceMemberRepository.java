package com.platform.uop.workspace.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

  Optional<WorkspaceMember> findByWorkspaceIdAndUserEmail(
      UUID workspaceId,
      String email);

  List<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId);

  boolean existsByWorkspaceIdAndUserId(
      UUID workspaceId,
      UUID userId);

  void deleteByWorkspaceIdAndUserId(
      UUID workspaceId,
      UUID userId);

  boolean existsByWorkspaceIdAndUserIdAndRoleIn(
      UUID workspaceId,
      UUID userId,
      Collection<WorkspaceRole> roles);

  boolean existsByWorkspaceIdAndUserIdAndRole(
      UUID workspaceId,
      UUID userId,
      WorkspaceRole role);
}
