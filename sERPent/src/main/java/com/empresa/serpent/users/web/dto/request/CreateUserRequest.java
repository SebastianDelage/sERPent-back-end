package com.empresa.serpent.users.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.empresa.serpent.users.domain.enums.UserRole;

import java.util.List;

public record CreateUserRequest(

        @NotBlank(message = "El nombre es obligatorio.")
        String name,

        @Size(max = 100, message = "El apellido no puede tener más de {max} caracteres.")
        String lastName,

        @NotBlank(message = "El usuario es obligatorio.")
        String username,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 6, message = "La contraseña tiene que tener al menos {min} caracteres.")
        String password,

        @Email(message = "El email no tiene un formato válido.")
        String email,

        Boolean active,

    /** Omit for the narrower role: a user created without an explicit role is an EMPLOYEE. */
        UserRole role,

        /** Warehouses this user may operate in. At least one active warehouse is required. */
        List<Long> warehouseIds
) {
}