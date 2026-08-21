package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.empresa.serpent.users.domain.enums.UserRole;

import java.util.List;

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

        Boolean active,

    /** Omit to leave the current role untouched. */
        UserRole role,

        /**
         * Warehouses this user may operate in. Omit (null) to leave the current assignment
         * untouched; when present it replaces the assignment and must name at least one
         * active warehouse.
         */
        List<Long> warehouseIds
) {
}