package com.darshan.ecommerce.product;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.common.exception.InsufficientStockException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductResponseDTO create(CreateProductRequestDTO request) {
        Product product = new Product(request.getSku(), request.getName(), request.getDescription(),
                request.getPrice(), request.getStockQuantity(), category(request.getCategoryId()));
        return mapToDTO(productRepository.save(product));
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

    public ProductResponseDTO update(Long id, CreateProductRequestDTO request) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
        existing.setSku(request.getSku());
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setStockQuantity(request.getStockQuantity());
        existing.setCategory(category(request.getCategoryId()));
        return mapToDTO(productRepository.save(existing));
    }

    private Category category(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + categoryId));
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
        dto.setSku(product.getSku());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStockQuantity(product.getStockQuantity());
        return dto;
    }
}

