package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,
        String lastName,
        @NotBlank(message = "Username cannot be blank")
        String username,
        @NotBlank(message = "Password hash cannot be blank")
        String passwordHash,
        String email,
        Boolean active
) {
}