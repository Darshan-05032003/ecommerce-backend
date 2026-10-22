package com.example.ecommerce.service;

import com.example.ecommerce.dto.ProductResponseDTO;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponseDTO create(Product product) {
        Product saved = productRepository.save(product);
        return mapToDTO(saved);
    }

    public ProductResponseDTO getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
        return mapToDTO(product);
    }

    public Page<ProductResponseDTO> listAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::mapToDTO);
    }

    public List<ProductResponseDTO> searchByName(String query) {
        return productRepository.findByNameContainingIgnoreCase(query)
                .stream().map(this::mapToDTO).toList();
    }

    public ProductResponseDTO update(Long id, Product updated) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setPrice(updated.getPrice());
        existing.setStockQuantity(updated.getStockQuantity());
        Product saved = productRepository.save(existing);
        return mapToDTO(saved);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + productId));
        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException("Insufficient stock for product " + productId);
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);
    }

    private ProductResponseDTO mapToDTO(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStockQuantity(product.getStockQuantity());
        return dto;
    }
}

