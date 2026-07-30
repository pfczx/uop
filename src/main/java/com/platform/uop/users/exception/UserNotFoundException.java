package com.platform.uop.users.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

   public UserNotFoundException(UUID id) {
        super("User with id '%s' not found".formatted(id));
    }

    public UserNotFoundException(String email) {
        super("User with email '%s' not found".formatted(email));
    }
}
