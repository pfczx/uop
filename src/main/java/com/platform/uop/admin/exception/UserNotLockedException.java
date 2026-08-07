package com.platform.uop.admin.exception;

import java.util.UUID;

public class UserNotLockedException extends RuntimeException {

    public UserNotLockedException(UUID id) {
        super("User with id '%s' is not locked".formatted(id));
    }
}