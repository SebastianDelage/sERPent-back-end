package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        String lastName,

        @NotBlank(message = "Username cannot be blank")
        String username,

        @NotBlank(message = "Password hash cannot be blank")
        String passwordHash,

        @Email(message = "Email must be a valid email address")
        String email,

        Boolean active
) {
}