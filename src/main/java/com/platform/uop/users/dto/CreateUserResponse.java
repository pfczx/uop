package com.platform.uop.users.dto;

import java.time.Instant;
import java.util.UUID;

import com.platform.uop.users.enums.UserRole;
import com.platform.uop.users.enums.UserStatus;

public record CreateUserResponse(

    UUID id,

    String email,

    UserRole role,

    UserStatus status,

    Instant createdAt

) {
}
