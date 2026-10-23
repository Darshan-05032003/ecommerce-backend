package com.darshan.ecommerce.user;

import com.darshan.ecommerce.common.openapi.ApiErrorResponses;
import com.darshan.ecommerce.common.openapi.ApiTags;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = ApiTags.ADMIN)
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all users")
    public List<UserResponse> list() {
        return userService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any user by id")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }
}
