package com.empresa.serpent.users.web.dto.response;

import com.empresa.serpent.users.domain.enums.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String lastName,
        String username,
        String email,
        Boolean active,
        UserRole role,
        LocalDateTime createdAt
) {
}