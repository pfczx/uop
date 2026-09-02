package com.platform.uop.admin.exception;

import java.util.UUID;

public class UserAlreadyDeactivatedException extends RuntimeException {

  public UserAlreadyDeactivatedException(UUID id) {
    super("User with id %s is already deactivated".formatted(id));
  }

}
