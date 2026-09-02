package com.platform.uop.auth.dto;

import java.util.UUID;

import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;

public record MeResponse(
    UUID id,
    String email,
    UserRole role,
    UserStatus status

) {
}
