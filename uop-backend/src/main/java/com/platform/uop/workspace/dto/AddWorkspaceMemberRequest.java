package com.platform.uop.workspace.dto;

import java.util.UUID;

import com.platform.uop.workspace.enums.WorkspaceRole;

public record AddWorkspaceMemberRequest(
    UUID userId,
    WorkspaceRole role) {
}
