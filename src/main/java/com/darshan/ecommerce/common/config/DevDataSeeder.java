package com.darshan.ecommerce.common.config;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.Role;
import com.darshan.ecommerce.user.RoleRepository;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

// Development data only. Roles are seeded by Flyway in every profile; this runner adds an admin
// account and a small catalogue so the API is usable right after "docker compose up".
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final SeedProperties properties;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(SeedProperties properties,
                         RoleRepository roleRepository,
                         UserRepository userRepository,
                         CategoryRepository categoryRepository,
                         ProductRepository productRepository,
                         PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedCatalogue();
    }

    private void seedAdmin() {
        if (userRepository.existsByEmail(properties.adminEmail())) {
            return;
        }
        Role adminRole = roleRepository.findByName(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Role " + Role.ADMIN + " is not seeded"));
        userRepository.save(new User(properties.adminEmail(), passwordEncoder.encode(properties.adminPassword()),
                "Administrator", Set.of(adminRole)));
        log.info("Seeded development admin account");
    }

    private void seedCatalogue() {
        if (productRepository.count() > 0) {
            return;
        }
        Category electronics = categoryRepository.save(new Category("Electronics", "Computers, phones and accessories"));
        Category books = categoryRepository.save(new Category("Books", "Printed and electronic books"));

        List<Product> products = List.of(
                product("LAP-14-001", "Laptop", "14 inch lightweight laptop", "75000.00", 10, electronics),
                product("MOU-WL-001", "Wireless Mouse", "Ergonomic wireless mouse", "1200.00", 50, electronics),
                product("KEY-MC-001", "Mechanical Keyboard", "Tenkeyless keyboard with brown switches", "4500.00", 25, electronics),
                product("BK-JAVA-001", "Effective Java", "Third edition, Joshua Bloch", "2800.00", 40, books),
                product("BK-DDIA-001", "Designing Data-Intensive Applications", "Martin Kleppmann", "3200.00", 15, books));
        productRepository.saveAll(products);
        log.info("Seeded {} categories and {} products", 2, products.size());
    }

    private static Product product(String sku, String name, String description, String price, int stock,
                                   Category category) {
        Product product = new Product(sku, name, description, new BigDecimal(price), stock);
        product.setCategory(category);
        return product;
    }
}
