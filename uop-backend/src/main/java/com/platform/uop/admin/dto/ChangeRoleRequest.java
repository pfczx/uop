package com.platform.uop.admin.dto;

import com.platform.uop.users.enums.UserRole;

public record ChangeRoleRequest(

    UserRole role

) {
}