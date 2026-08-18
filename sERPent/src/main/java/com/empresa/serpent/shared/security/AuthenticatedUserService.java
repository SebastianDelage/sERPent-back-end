package com.empresa.serpent.shared.security;

import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single source of truth for "who is making this request".
 *
 * <p>Until this class existed, every application service took the acting user from
 * {@code request.createdByUserId()} — a client-supplied body field. That made any
 * per-user authorization check meaningless, since a caller could simply name a
 * different user. The acting user now comes from the {@link SecurityContextHolder},
 * populated by {@link JwtAuthenticationFilter} from the bearer token.
 *
 * <p>The principal is the username (the JWT subject), not the id, so the lookup is by
 * username.
 */
@Service
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    /** The user behind the current request. Throws when there is none, or it no longer exists. */
    @Transactional(readOnly = true)
    public UserEntity requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Tenés que iniciar sesión para realizar esta acción.");
        }

        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ForbiddenException(
                        "Tu usuario ya no existe. Iniciá sesión de nuevo."));
    }

    /**
     * Rejects a request that names a different user than the authenticated one.
     *
     * <p>An out-of-date client that still sends {@code createdByUserId} must find out, rather
     * than silently having its operation recorded under someone else's name. A null value is
     * accepted: newer clients are expected to stop sending the field altogether.
     */
    public void requireMatchingCreatedByUserId(Long createdByUserId, UserEntity currentUser) {
        if (createdByUserId != null && !createdByUserId.equals(currentUser.getId())) {
            throw new ValidationException(
                    "La operación se intentó registrar a nombre de otro usuario. "
                            + "Actualizá la aplicación e iniciá sesión de nuevo.");
        }
    }
}
