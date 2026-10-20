package com.platform.uop.workspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RemoveWorkspaceMemeberRequest(

    @NotBlank @Email String email

) {

}
