package com.platform.uop.admin.exception;

import java.util.UUID;

public class UserSelfModificationException extends RuntimeException {

    public UserSelfModificationException(UUID id) {
        super("Cannot perform this action on your own account".formatted(id));
    }
}