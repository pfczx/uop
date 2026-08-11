package com.platform.uop.admin.exception;

import java.util.UUID;

public class UserAlreadyDeactivatedException extends RuntimeException {

  public UserAlreadyDeactivatedException(UUID id) {
    super("User `%s`  already deactivated".formatted(id));
  }

}
