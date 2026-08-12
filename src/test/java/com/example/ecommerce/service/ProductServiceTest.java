package com.example.ecommerce.service;

import com.example.ecommerce.dto.ProductResponseDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setDescription("Test Description");
        product.setPrice(new BigDecimal("10.00"));
        product.setStockQuantity(100);
    }

    @Test
    void getById_ShouldReturnProductDTO() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        
        ProductResponseDTO result = productService.getById(1L);
        
        assertNotNull(result);
        assertEquals("Test Product", result.getName());
    }

    @Test
    void getById_WhenNotFound_ShouldThrowException() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResourceNotFoundException.class, () -> productService.getById(1L));
    }

    @Test
    void listAll_ShouldReturnPaginatedProducts() {
        Page<Product> page = new PageImpl<>(Collections.singletonList(product));
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(page);
        
        Page<ProductResponseDTO> result = productService.listAll(PageRequest.of(0, 10));
        
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Product", result.getContent().get(0).getName());
    }
}
