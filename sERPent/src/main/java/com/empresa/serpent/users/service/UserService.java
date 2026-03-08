package com.empresa.serpent.users.service;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import com.empresa.serpent.users.web.dto.request.CreateUserRequest;
import com.empresa.serpent.users.web.dto.request.UpdateUserRequest;
import com.empresa.serpent.users.web.dto.response.UserResponse;
import com.empresa.serpent.users.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        validateUsername(request.username(), null);
        validateEmail(request.email(), null);

        UserEntity entity = userMapper.toEntity(request);

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        normalizeFields(entity);

        UserEntity saved = userRepository.save(entity);
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        validateUsername(request.username(), id);
        validateEmail(request.email(), id);

        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        userMapper.updateEntityFromRequest(request, entity);
        normalizeFields(entity);

        UserEntity saved = userRepository.save(entity);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        return userMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAllActive() {
        return userRepository.findByActiveTrue().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    private void validateUsername(String username, Long currentUserId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }

        userRepository.findByUsername(username.trim())
                .ifPresent(existing -> {
                    if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                        throw new IllegalArgumentException("Username already exists: " + username.trim());
                    }
                });
    }

    private void validateEmail(String email, Long currentUserId) {
        if (email == null || email.isBlank()) {
            return;
        }

        userRepository.findByEmail(email.trim())
                .ifPresent(existing -> {
                    if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                        throw new IllegalArgumentException("Email already exists: " + email.trim());
                    }
                });
    }

    private void normalizeFields(UserEntity entity) {
        if (entity.getUsername() != null) {
            entity.setUsername(entity.getUsername().trim());
        }

        if (entity.getEmail() != null && entity.getEmail().isBlank()) {
            entity.setEmail(null);
        }

        if (entity.getEmail() != null) {
            entity.setEmail(entity.getEmail().trim());
        }

        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }

        if (entity.getLastName() != null && entity.getLastName().isBlank()) {
            entity.setLastName(null);
        }

        if (entity.getLastName() != null) {
            entity.setLastName(entity.getLastName().trim());
        }
    }
}