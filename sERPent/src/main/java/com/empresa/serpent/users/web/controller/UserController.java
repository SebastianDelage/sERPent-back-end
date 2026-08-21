package com.empresa.serpent.users.web.controller;

import com.empresa.serpent.inventory.web.dto.response.WarehouseResponse;
import com.empresa.serpent.users.service.UserService;
import com.empresa.serpent.users.web.dto.request.CreateUserRequest;
import com.empresa.serpent.users.web.dto.request.UpdateUserRequest;
import com.empresa.serpent.users.web.dto.request.UpdateUserWarehousesRequest;
import com.empresa.serpent.users.web.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("hasRole('ADMIN')")
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
    public List<UserResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return userService.search(name, includeInactive);
    }

    /**
     * The caller's own warehouses, for the session warehouse selector. Deliberately an
     * endpoint and not a JWT claim: reassigning a warehouse must take effect immediately,
     * not on the user's next login once the current token expires.
     *
     * <p>Declared before {@code /{id}/warehouses} so the literal path wins over the template.
     */
    // Overrides the class-level ADMIN rule: this returns only the caller's OWN
    // branches, and the session branch selector would not work without it.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/warehouses")
    public List<WarehouseResponse> findMyWarehouses() {
        return userService.findWarehousesOfCurrentUser();
    }

    @GetMapping("/{id}/warehouses")
    public List<WarehouseResponse> findWarehouses(@PathVariable Long id) {
        return userService.findWarehouses(id);
    }

    @PutMapping("/{id}/warehouses")
    public List<WarehouseResponse> replaceWarehouses(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserWarehousesRequest request
    ) {
        return userService.replaceWarehouses(id, request.warehouseIds());
    }
}