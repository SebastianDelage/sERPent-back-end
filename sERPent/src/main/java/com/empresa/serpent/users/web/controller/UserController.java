package com.empresa.serpent.users.web.controller;

import com.empresa.serpent.users.service.UserService;
import com.empresa.serpent.users.web.dto.request.CreateUserRequest;
import com.empresa.serpent.users.web.dto.request.UpdateUserRequest;
import com.empresa.serpent.users.web.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.update(id, request);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @GetMapping
    public List<UserResponse> findAllActive() {
        return userService.findAllActive();
    }

    @GetMapping("/all")
    public List<UserResponse> findAll() {
        return userService.findAll();
    }
}