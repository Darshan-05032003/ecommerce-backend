package com.darshan.ecommerce.product;

import com.darshan.ecommerce.common.openapi.ApiErrorResponses;
import com.darshan.ecommerce.common.openapi.ApiTags;
import com.darshan.ecommerce.common.web.PageParams;
import com.darshan.ecommerce.common.web.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = ApiTags.ADMIN)
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List all products, active or not")
    public PageResponse<ProductResponse> list(@ParameterObject @Valid ProductFilter filter,
                                              @ParameterObject @Valid PageParams pageParams) {
        return productService.listAll(filter, pageParams.toPageable(ProductController.SORT));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any product by id, including soft-deleted ones")
    @ApiResponse(responseCode = "200", description = "The product")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    public ResponseEntity<ProductResponse> create(@RequestBody @Valid CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product; the request must carry the version that was read")
    @ApiResponse(responseCode = "200", description = "Product updated")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = ApiErrorResponses.CONFLICT)
    public ProductResponse update(@PathVariable Long id, @RequestBody @Valid UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a product so it disappears from the public catalogue")
    @ApiResponse(responseCode = "204", description = "Product deactivated")
    @ApiResponse(responseCode = "404", ref = ApiErrorResponses.NOT_FOUND)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
