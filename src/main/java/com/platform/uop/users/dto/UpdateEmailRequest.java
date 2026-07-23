package com.platform.uop.users.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateEmailRequest(
    @NotBlank UUID id,
    @NotBlank @Email String newEmail

) {

}
