package com.platform.uop.workspace.dto;

import com.platform.uop.workspace.enums.WorkspaceRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceMemberRequest(
    @NotNull @Email String email,
    @NotNull WorkspaceRole role

) {
}
