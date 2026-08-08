package com.platform.uop.setup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
    @NotBlank @Email String email,

    @NotBlank @Size(min = 8, max = 100) String password

) {

}
