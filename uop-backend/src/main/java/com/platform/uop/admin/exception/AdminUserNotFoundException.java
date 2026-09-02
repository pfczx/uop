package com.platform.uop.admin.exception;

import java.util.UUID;

public class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException(UUID id) {
        super("User with id '%s' not found".formatted(id));
    }

    public AdminUserNotFoundException(String email) {
        super("User with email '%s' not found".formatted(email));
    }
}