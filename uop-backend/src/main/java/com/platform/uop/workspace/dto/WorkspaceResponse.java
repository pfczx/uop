package com.platform.uop.workspace.dto;

import com.platform.uop.workspace.entity.Workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id,
    String name,
    String description,
    Instant createdAt,
    Instant updatedAt) {

  public static WorkspaceResponse from(Workspace workspace) {
    return new WorkspaceResponse(
        workspace.getId(),
        workspace.getName(),
        workspace.getDescription(),
        workspace.getCreatedAt(),
        workspace.getUpdatedAt());
  }
}
