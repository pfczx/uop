package com.platform.uop.workspace.dto;

import com.platform.uop.workspace.enums.WorkspaceRole;

public record UpdateWorkspaceMemberRoleRequest(
    WorkspaceRole role) {

}
