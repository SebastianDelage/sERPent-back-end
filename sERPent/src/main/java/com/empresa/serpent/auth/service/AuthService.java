package com.empresa.serpent.auth.service;

import com.empresa.serpent.auth.web.dto.request.LoginRequest;
import com.empresa.serpent.auth.web.dto.response.LoginResponse;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.shared.security.JwtService;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );
        } catch (BadCredentialsException ex) {
            throw new ValidationException("Usuario o contraseña incorrectos.");
        }

        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ValidationException("Usuario o contraseña incorrectos."));

        String token = jwtService.generateToken(user.getUsername(), user.getId());

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getName()
        );
    }
}