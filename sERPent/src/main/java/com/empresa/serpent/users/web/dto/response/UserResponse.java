package com.empresa.serpent.users.web.dto.response;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String lastName,
        String username,
        String email,
        Boolean active,
        LocalDateTime createdAt
) {
}