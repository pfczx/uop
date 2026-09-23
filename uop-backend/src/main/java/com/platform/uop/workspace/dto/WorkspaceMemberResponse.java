package com.platform.uop.workspace.dto;

import java.time.Instant;
import java.util.UUID;

import com.platform.uop.workspace.enums.WorkspaceRole;

public record WorkspaceMemberResponse(
    UUID userId,
    String email,
    WorkspaceRole role,
    Instant createdAt) {
}
