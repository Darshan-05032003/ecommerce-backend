package com.darshan.ecommerce.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Public product catalogue")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ProductResponseDTO getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List products with pagination")
    public ResponseEntity<?> list(@RequestParam(value = "q", required = false) String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(productService.listAll(pageable));
        }
        return ResponseEntity.ok(productService.searchByName(query));
    }
}
