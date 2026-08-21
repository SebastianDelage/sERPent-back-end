package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.empresa.serpent.users.domain.enums.UserRole;

import java.util.List;

public record CreateUserRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        @Size(max = 100, message = "Last name cannot be longer than 100 characters")
        String lastName,

        @NotBlank(message = "Username cannot be blank")
        String username,

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        @Email(message = "Email must be a valid email address")
        String email,

        Boolean active,

    /** Omit for the narrower role: a user created without an explicit role is an EMPLOYEE. */
        UserRole role,

        /** Warehouses this user may operate in. At least one active warehouse is required. */
        List<Long> warehouseIds
) {
}