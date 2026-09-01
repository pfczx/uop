package com.platform.uop.users.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePasswordRequest(

    @NotBlank String newPassword,
    @NotBlank String oldPassword

) {

}
