package com.platform.uop.admin.exception;

public class CannotModifyAdminException extends RuntimeException {

    public CannotModifyAdminException() {
        super("Cannot modify admin user");
    }
}