package com.platform.uop.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record LockUserRequest(

    @NotBlank String reason

) {

  public LockUserRequest() {
    this("blank");
  }
} 
