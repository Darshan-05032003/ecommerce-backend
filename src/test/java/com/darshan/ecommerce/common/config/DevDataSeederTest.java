package com.darshan.ecommerce.common.config;

import com.darshan.ecommerce.TestcontainersConfiguration;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.Role;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.seed.admin-email=seed-admin@example.com",
        "app.seed.admin-password=SeededPass1!"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "dev"})
@Transactional
class DevDataSeederTest {

    @Autowired
    private DevDataSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedsAdminWithHashedPasswordFromConfiguration() {
        User admin = userRepository.findByEmail("seed-admin@example.com").orElseThrow();

        assertThat(admin.getPassword()).isNotEqualTo("SeededPass1!");
        assertThat(passwordEncoder.matches("SeededPass1!", admin.getPassword())).isTrue();
        assertThat(admin.getRoles()).extracting(Role::getName).containsExactly(Role.ADMIN);
        assertThat(admin.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void seedsSampleCatalogueOnlyOnce() {
        seeder.run(new DefaultApplicationArguments());

        assertThat(categoryRepository.count()).isEqualTo(2);
        assertThat(productRepository.count()).isEqualTo(5);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(productRepository.findAll()).allSatisfy(product -> {
            assertThat(product.isActive()).isTrue();
            assertThat(product.getCategory()).isNotNull();
        });
    }
}
