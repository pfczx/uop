package com.platform.uop.admin.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.platform.uop.admin.exception.AdminUserNotFoundException;
import com.platform.uop.admin.exception.CannotModifyAdminException;
import com.platform.uop.admin.exception.UserAlreadyDeactivatedException;
import com.platform.uop.admin.exception.UserAlreadyLockedException;
import com.platform.uop.admin.exception.UserNotLockedException;
import com.platform.uop.setup.exception.SetupAlreadyCompletedException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(CannotModifyAdminException.class)
    public ResponseEntity<Map<String, String>> handleCannotModifyAdmin(CannotModifyAdminException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UserAlreadyDeactivatedException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyDeactivated(UserAlreadyDeactivatedException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UserAlreadyLockedException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyLocked(UserAlreadyLockedException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UserNotLockedException.class)
    public ResponseEntity<Map<String, String>> handleUserNotLocked(UserNotLockedException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AdminUserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAdminUserNotFound(AdminUserNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SetupAlreadyCompletedException.class)
    public ResponseEntity<Map<String, String>> handleSetupAlreadyCompleted(SetupAlreadyCompletedException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
}