package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        @Size(max = 100, message = "Last name cannot be longer than 100 characters")
        String lastName,

        @NotBlank(message = "Username cannot be blank")
        String username,

        // Optional on update: if null/blank, the current password is kept.
        String password,

        @Email(message = "Email must be a valid email address")
        String email,

        Boolean active
) {
}