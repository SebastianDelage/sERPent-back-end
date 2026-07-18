package com.empresa.serpent.users.service;

import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import com.empresa.serpent.users.web.dto.request.CreateUserRequest;
import com.empresa.serpent.users.web.dto.request.UpdateUserRequest;
import com.empresa.serpent.users.web.dto.response.UserResponse;
import com.empresa.serpent.users.web.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, passwordEncoder);
    }

    private CreateUserRequest createRequest(String username, String password, String email, Boolean active) {
        return new CreateUserRequest("Juan", "Pérez", username, password, email, active);
    }

    private UpdateUserRequest updateRequest(String username, String password, String email, Boolean active) {
        return new UpdateUserRequest("Juan", "Pérez", username, password, email, active);
    }

    // --- create ---

    @Test
    @DisplayName("Should hash the password on create")
    void shouldHashThePasswordOnCreate() {
        CreateUserRequest request = createRequest("jperez", "secret123", "juan@test.com", true);

        when(userRepository.findByUsername("jperez")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        // The stored value is the hash, never the plaintext.
        assertEquals("HASHED", captor.getValue().getPasswordHash());
        verify(passwordEncoder).encode("secret123");
    }

    @Test
    @DisplayName("Should default active to true when null on create")
    void shouldDefaultActiveToTrueWhenNullOnCreate() {
        CreateUserRequest request = createRequest("jperez", "secret123", null, null);

        when(userRepository.findByUsername("jperez")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals(true, captor.getValue().getActive());
    }

    @Test
    @DisplayName("Should throw when username already exists on create")
    void shouldThrowWhenUsernameAlreadyExistsOnCreate() {
        CreateUserRequest request = createRequest("jperez", "secret123", null, true);

        UserEntity existing = UserEntity.builder().id(99L).username("jperez").build();
        when(userRepository.findByUsername("jperez")).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.create(request)
        );

        assertEquals("Ya existe un usuario con el nombre \"jperez\".", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when username is blank on create")
    void shouldThrowWhenUsernameIsBlankOnCreate() {
        CreateUserRequest request = createRequest("   ", "secret123", null, true);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> userService.create(request)
        );

        assertEquals("El nombre de usuario es obligatorio.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when email already exists on create")
    void shouldThrowWhenEmailAlreadyExistsOnCreate() {
        CreateUserRequest request = createRequest("jperez", "secret123", "juan@test.com", true);

        UserEntity existing = UserEntity.builder().id(99L).email("juan@test.com").build();
        when(userRepository.findByUsername("jperez")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.create(request)
        );

        assertEquals("Ya existe un usuario con el email \"juan@test.com\".", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    // --- update ---

    @Test
    @DisplayName("Should re-hash the password on update when a new one is given")
    void shouldRehashThePasswordOnUpdateWhenGiven() {
        UpdateUserRequest request = updateRequest("jperez", "newsecret", null, true);

        UserEntity existing = UserEntity.builder()
                .id(5L).username("jperez").passwordHash("OLD").active(true).build();

        when(userRepository.findByUsername("jperez")).thenReturn(Optional.of(existing));
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newsecret")).thenReturn("NEWHASH");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.update(5L, request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("NEWHASH", captor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("Should keep the current password on update when none is given")
    void shouldKeepPasswordOnUpdateWhenNoneGiven() {
        UpdateUserRequest request = updateRequest("jperez", null, null, true);

        UserEntity existing = UserEntity.builder()
                .id(5L).username("jperez").passwordHash("OLD").active(true).build();

        when(userRepository.findByUsername("jperez")).thenReturn(Optional.of(existing));
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.update(5L, request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        // Untouched: the encoder is never called and the old hash stays.
        assertEquals("OLD", captor.getValue().getPasswordHash());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Should reject deactivating the protected admin user")
    void shouldRejectDeactivatingTheProtectedAdmin() {
        UpdateUserRequest request = updateRequest("admin", null, null, false);

        UserEntity admin = UserEntity.builder()
                .id(1L).username("admin").passwordHash("H").active(true).build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> userService.update(1L, request)
        );

        assertEquals("El usuario administrador no se puede desactivar.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow deactivating a non-protected user")
    void shouldAllowDeactivatingANonProtectedUser() {
        UpdateUserRequest request = updateRequest("jperez", null, null, false);

        UserEntity existing = UserEntity.builder()
                .id(5L).username("jperez").passwordHash("H").active(true).build();

        when(userRepository.findByUsername("jperez")).thenReturn(Optional.of(existing));
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.update(5L, request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals(false, captor.getValue().getActive());
    }

    @Test
    @DisplayName("Should throw when the user to update is not found")
    void shouldThrowWhenUserToUpdateNotFound() {
        UpdateUserRequest request = updateRequest("jperez", null, null, true);

        when(userRepository.findByUsername("jperez")).thenReturn(Optional.empty());
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.update(5L, request));
        verify(userRepository, never()).save(any());
    }

    // --- findById ---

    @Test
    @DisplayName("Should throw when user not found by id")
    void shouldThrowWhenUserNotFoundById() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.findById(42L));
    }

    // --- search ---

    @Test
    @DisplayName("Should delegate search to the repository with the includeInactive flag")
    void shouldDelegateSearchToRepository() {
        UserEntity u1 = UserEntity.builder().id(1L).name("Ana").username("ana").active(true).build();
        UserEntity u2 = UserEntity.builder().id(2L).name("Beto").username("beto").active(false).build();

        when(userRepository.search(true)).thenReturn(List.of(u1, u2));

        List<UserResponse> result = userService.search(true);

        assertEquals(2, result.size());
        assertEquals("ana", result.get(0).username());
        assertEquals("beto", result.get(1).username());
        verify(userRepository).search(true);
    }
}