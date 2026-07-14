package com.empresa.serpent.auth.web.dto.response;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String name
) {
}