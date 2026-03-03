package com.example.ecommerce.service;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
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

    public Product create(Product product) {
        return productRepository.save(product);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id " + id));
    }

    public List<Product> listAll() {
        return productRepository.findAll();
    }

    public List<Product> searchByName(String query) {
        return productRepository.findByNameContainingIgnoreCase(query);
    }

    public Product update(Long id, Product updated) {
        Product existing = getById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setPrice(updated.getPrice());
        existing.setStockQuantity(updated.getStockQuantity());
        return productRepository.save(existing);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    public void decreaseStock(Long productId, int quantity) {
        Product product = getById(productId);
        if (product.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient stock for product " + productId);
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);
    }
}

