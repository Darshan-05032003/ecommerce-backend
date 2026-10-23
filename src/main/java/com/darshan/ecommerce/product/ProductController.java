package com.darshan.ecommerce.product;

import com.darshan.ecommerce.common.web.PageParams;
import com.darshan.ecommerce.common.web.PageResponse;
import com.darshan.ecommerce.common.web.SortWhitelist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Public product catalogue")
public class ProductController {

    static final SortWhitelist SORT = SortWhitelist.of("name", "price", "createdAt");

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List active products with filtering, sorting and pagination")
    public PageResponse<ProductResponse> list(@ParameterObject @Valid ProductFilter filter,
                                              @ParameterObject @Valid PageParams pageParams) {
        return productService.listActive(filter, pageParams.toPageable(SORT));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active product by id")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getActiveById(id);
    }
}
