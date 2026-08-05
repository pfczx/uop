package com.platform.uop.auth.dto;

import java.util.UUID;

import com.platform.uop.users.enums.UserRole;

public record LogoutResponse(
    UUID id,
    String email,
    UserRole role,
    String message

) {

}
