package com.platform.uop.auth.dto;

import java.util.UUID;

import com.platform.uop.users.enums.UserRole;

public record LoginResponse(

    UUID id,

    String email,

    UserRole role

) {
}
