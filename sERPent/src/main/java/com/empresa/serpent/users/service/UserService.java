package com.empresa.serpent.users.service;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.response.WarehouseResponse;
import com.empresa.serpent.inventory.web.mapper.WarehouseMapper;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import com.empresa.serpent.users.web.dto.request.CreateUserRequest;
import com.empresa.serpent.users.web.dto.request.UpdateUserRequest;
import com.empresa.serpent.users.web.dto.response.UserResponse;
import com.empresa.serpent.users.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;



@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    private final AuthenticatedUserService authenticatedUserService;

    /** Seeded admin: it must always stay usable, so it can't be deactivated. */
    private static final Long PROTECTED_USER_ID = 1L;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        validateUsername(request.username(), null);
        validateEmail(request.email(), null);

        UserEntity entity = userMapper.toEntity(request);

        entity.setPasswordHash(passwordEncoder.encode(request.password()));

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        // A brand new user always needs somewhere to operate: creating one without
        // warehouses would just produce an account that cannot do anything.
        entity.setWarehouses(resolveRequiredWarehouses(request.warehouseIds()));

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

        if (PROTECTED_USER_ID.equals(id) && Boolean.FALSE.equals(request.active())) {
            throw new ValidationException("El usuario administrador no se puede desactivar.");
        }

        userMapper.updateEntityFromRequest(request, entity);

        if (request.password() != null && !request.password().isBlank()) {
            entity.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        /*
         Only touched when the request actually carries the field. Omitting it leaves the
         current assignment alone, which is what lets an existing user with no warehouses
         (possible after the V19 backfill on an installation that had none) still be edited
         at all. The "at least one" rule is a write-time validation on the assignment itself,
         not a retroactive invariant every update has to satisfy.
         */
        if (request.warehouseIds() != null) {
            entity.setWarehouses(resolveRequiredWarehouses(request.warehouseIds()));
        }

        normalizeFields(entity);

        UserEntity saved = userRepository.save(entity);
        return userMapper.toResponse(saved);
    }

    /** The warehouses a user may operate in. */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> findWarehouses(Long userId) {
        UserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        return toWarehouseResponses(entity);
    }

    /** Replaces a user's warehouse assignment wholesale. */
    @Transactional
    public List<WarehouseResponse> replaceWarehouses(Long userId, List<Long> warehouseIds) {
        UserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        entity.setWarehouses(resolveRequiredWarehouses(warehouseIds));

        return toWarehouseResponses(userRepository.save(entity));
    }

    /** The warehouses the caller may operate in, for the session warehouse selector. */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> findWarehousesOfCurrentUser() {
        return toWarehouseResponses(authenticatedUserService.requireCurrentUser());
    }

    private List<WarehouseResponse> toWarehouseResponses(UserEntity entity) {
        return entity.getWarehouses().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(warehouseMapper::toResponse)
                .toList();
    }

    /**
     * Resolves warehouse ids into entities, requiring at least one and rejecting inactive
     * ones: assigning a user only to a deactivated warehouse would leave them unable to
     * operate while looking correctly configured.
     */
    private Set<WarehouseEntity> resolveRequiredWarehouses(List<Long> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            throw new ValidationException("El usuario tiene que tener al menos un depósito asignado.");
        }

        Set<WarehouseEntity> warehouses = new LinkedHashSet<>();

        for (Long warehouseId : warehouseIds) {
            WarehouseEntity warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new NotFoundException("Warehouse not found: " + warehouseId));

            if (!Boolean.TRUE.equals(warehouse.getActive())) {
                throw new ValidationException(
                        "No podés asignar el depósito \"" + warehouse.getName() + "\" porque está inactivo.");
            }

            warehouses.add(warehouse);
        }

        return warehouses;
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        return userMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> search(String name, boolean includeInactive) {
        String term = (name == null || name.isBlank()) ? null : name.trim();
        return userRepository.search(term, includeInactive).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    private void validateUsername(String username, Long currentUserId) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("El nombre de usuario es obligatorio.");
        }

        userRepository.findByUsername(username.trim())
                .ifPresent(existing -> {
                    if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                        throw new ConflictException("Ya existe un usuario con el nombre \"" + username.trim() + "\".");
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
                        throw new ConflictException("Ya existe un usuario con el email \"" + email.trim() + "\".");
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