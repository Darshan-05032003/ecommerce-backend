package com.example.ecommerce.config;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.Role;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.RoleRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CommandLineRunner seedDatabase(RoleRepository roleRepository,
                                  UserRepository userRepository,
                                  ProductRepository productRepository,
                                  PasswordEncoder passwordEncoder) {
        return args -> {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setName("ADMIN");
                        return roleRepository.save(role);
                    });

            if (userRepository.count() == 0) {
                User admin = User.builder()
                        .email("admin@example.com")
                        .fullName("Admin User")
                        .password(passwordEncoder.encode("admin123"))
                        .roles(Set.of(adminRole))
                        .build();
                userRepository.save(admin);
            }

            if (productRepository.count() == 0) {
                List<Product> products = List.of(
                        Product.builder()
                                .name("Laptop")
                                .description("14 inch lightweight laptop")
                                .price(new BigDecimal("75000.00"))
                                .stockQuantity(10)
                                .build(),
                        Product.builder()
                                .name("Wireless Mouse")
                                .description("Ergonomic wireless mouse")
                                .price(new BigDecimal("1200.00"))
                                .stockQuantity(50)
                                .build()
                );
                productRepository.saveAll(products);
            }
        };
    }
}

