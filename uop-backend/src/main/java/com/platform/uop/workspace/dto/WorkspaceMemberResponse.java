package com.platform.uop.workspace.dto;

import java.time.Instant;
import java.util.UUID;

import com.platform.uop.workspace.entity.WorkspaceMember;
import com.platform.uop.workspace.enums.WorkspaceRole;

public record WorkspaceMemberResponse(
    UUID userId,
    WorkspaceRole role,
    Instant createdAt) {

  public static WorkspaceMemberResponse from(WorkspaceMember member) {
    return new WorkspaceMemberResponse(
        member.getId(),
        member.getRole(),
        member.getCreatedAt());

  }
}
