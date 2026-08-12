package com.example.ecommerce.controller;

import com.example.ecommerce.dto.CreateProductRequestDTO;
import com.example.ecommerce.dto.ProductResponseDTO;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product Management APIs")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new product (Admin only)")
    public ResponseEntity<ProductResponseDTO> create(@RequestBody @Valid CreateProductRequestDTO requestDTO) {
        Product product = new Product();
        product.setName(requestDTO.getName());
        product.setDescription(requestDTO.getDescription());
        product.setPrice(requestDTO.getPrice());
        product.setStockQuantity(requestDTO.getStockQuantity());
        
        ProductResponseDTO created = productService.create(product);
        return ResponseEntity
                .created(URI.create("/api/products/" + created.getId()))
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by ID")
    public ProductResponseDTO getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping
    @Operation(summary = "Get all products with pagination")
    public ResponseEntity<?> list(
            @RequestParam(value = "q", required = false) String query,
            Pageable pageable) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(productService.listAll(pageable));
        }
        return ResponseEntity.ok(productService.searchByName(query));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing product (Admin only)")
    public ProductResponseDTO update(@PathVariable Long id, @RequestBody @Valid CreateProductRequestDTO requestDTO) {
        Product product = new Product();
        product.setName(requestDTO.getName());
        product.setDescription(requestDTO.getDescription());
        product.setPrice(requestDTO.getPrice());
        product.setStockQuantity(requestDTO.getStockQuantity());
        return productService.update(id, product);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a product (Admin only)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

