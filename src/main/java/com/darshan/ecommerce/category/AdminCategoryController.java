package com.darshan.ecommerce.category;

import com.darshan.ecommerce.common.openapi.ApiErrorResponses;
import com.darshan.ecommerce.common.openapi.ApiTags;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = ApiTags.ADMIN)
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @Operation(summary = "Create a category")
    @ApiResponse(responseCode = "201", description = "Category created")
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    public ResponseEntity<CategoryResponse> create(@RequestBody @Valid CategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    @ApiResponse(responseCode = "200", description = "Category updated")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    public CategoryResponse update(@PathVariable Long id, @RequestBody @Valid CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category that has no products")
    @ApiResponse(responseCode = "204", description = "Category deleted")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
