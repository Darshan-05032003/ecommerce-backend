package com.example.ecommerce.controller;

import com.example.ecommerce.dto.RegisterUserRequestDTO;
import com.example.ecommerce.dto.UserResponseDTO;
import com.example.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management APIs")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary = "Register a new user")
    public ResponseEntity<UserResponseDTO> register(@RequestBody @Valid RegisterUserRequestDTO request) {
        UserResponseDTO created = userService.register(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + created.getId()))
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userService.getEntityById(#id).email")
    public UserResponseDTO getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List all users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponseDTO> list() {
        return userService.listAll();
    }
}

