package com.platform.uop.setup.dto;

import java.util.UUID;

import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;

public record SetupResponse(
    UUID id,
    String email,
    UserRole role,
    UserStatus status

) {
}
