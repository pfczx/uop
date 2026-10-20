package com.platform.uop.workspace.dto;

import com.platform.uop.workspace.enums.WorkspaceRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddWorkspaceMemberRequest(
    @NotBlank @Email String email,

    @NotBlank WorkspaceRole role

) {
}
