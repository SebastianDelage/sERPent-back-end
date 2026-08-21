package com.empresa.serpent.shared.security;

import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Turns a bearer token into an authenticated principal carrying the user's role.
 *
 * <p>THE ROLE IS READ FROM THE DATABASE ON EVERY REQUEST, not from a claim in the token.
 * A claim would be cheaper, but tokens live for eight hours here, so demoting someone would
 * leave them with the permissions you just took away for the rest of their shift — and you
 * demote someone exactly when you have stopped trusting them. This mirrors the decision
 * already made for warehouse assignments, which are an endpoint and not a claim for the
 * same reason.
 *
 * <p>The same lookup also closes a hole that predates roles: until now a token kept working
 * after its user was deactivated or deleted, because {@code disabled(!active)} is only
 * evaluated at login. Now every request checks.
 *
 * <p>Cost is one indexed lookup per request. Write endpoints already did this through
 * {@link AuthenticatedUserService}, so the added cost falls only on reads. Caching it would
 * be optimising without evidence, and would reintroduce the staleness this avoids.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtService.isValid(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                authenticate(token, request);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authenticates the request when the token names a user who still exists and is still
     * active. Anything else leaves the context empty, which the entry point turns into a 401.
     */
    private void authenticate(String token, HttpServletRequest request) {
        String username = jwtService.extractUsername(token);

        Optional<UserEntity> found = userRepository.findByUsername(username);
        if (found.isEmpty()) {
            return;
        }

        UserEntity user = found.get();
        if (!Boolean.TRUE.equals(user.getActive())) {
            return;
        }

        // ROLE_ prefix is what hasRole('ADMIN') looks for; Spring adds it on the check side,
        // never on the authority side, so it has to be here.
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
