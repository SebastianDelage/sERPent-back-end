package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String name,
        String lastName,
        @NotBlank String username,
        @NotBlank String passwordHash,
        String email,
        Boolean active
) {
}