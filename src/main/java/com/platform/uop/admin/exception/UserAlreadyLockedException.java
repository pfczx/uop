package com.platform.uop.admin.exception;

import java.util.UUID;

public class UserAlreadyLockedException extends RuntimeException {

    public UserAlreadyLockedException(UUID id) {
        super("User with id %s is already locked".formatted(id));
    }
}