package com.platform.uop.setup.exception;

public class SetupAlreadyCompletedException extends RuntimeException {

  public SetupAlreadyCompletedException() {
    super("Setup already completed");
  }
}
