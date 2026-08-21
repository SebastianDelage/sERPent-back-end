package com.empresa.serpent.auth.web.dto.response;

import com.empresa.serpent.users.domain.enums.UserRole;

/**
 * @param role what this user may do. Sent so the client can hide what it would be refused
 *             anyway; it is a convenience for the UI and never the thing that enforces
 *             anything — the server re-reads the role on every request.
 */
public record LoginResponse(
        String token,
        Long userId,
        String username,
        String name,
        UserRole role
) {
}