package com.empresa.serpent.shared.security;

import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid credentials"));

        // Only used at login (DaoAuthenticationProvider). Per-request authentication goes
        // through JwtAuthenticationFilter, which reads the role the same way.
        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(user.getActive()))
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}