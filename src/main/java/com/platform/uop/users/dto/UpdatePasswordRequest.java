package com.platform.uop.users.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePasswordRequest(

    @NotBlank String Newpassword,
    @NotBlank String oldPassword

) {

}
