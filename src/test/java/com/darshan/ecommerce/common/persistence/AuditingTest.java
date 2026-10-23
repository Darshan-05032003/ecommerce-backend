package com.darshan.ecommerce.common.persistence;

import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class AuditingTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MutableClock clock;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser("alice@example.com")
    void fillsCreatedFieldsFromClockAndSecurityContextOnPersist() {
        Category saved = categoryRepository.saveAndFlush(new Category("Audited", null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(saved.getUpdatedAt()).isEqualTo(clock.instant());
        assertThat(saved.getCreatedBy()).isEqualTo("alice@example.com");
        assertThat(saved.getLastModifiedBy()).isEqualTo("alice@example.com");
    }

    @Test
    void usesSystemAsAuditorWhenNobodyIsAuthenticated() {
        Category saved = categoryRepository.saveAndFlush(new Category("Anonymous", null));

        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getLastModifiedBy()).isEqualTo("system");
    }

    @Test
    @WithMockUser("alice@example.com")
    void updatesOnlyTheModifiedFieldsOnChange() {
        Category saved = categoryRepository.saveAndFlush(new Category("Changing", null));
        Instant createdAt = saved.getCreatedAt();

        clock.advance(Duration.ofMinutes(5));
        authenticateAs("bob@example.com");
        saved.setDescription("Updated by bob");
        categoryRepository.saveAndFlush(saved);
        entityManager.clear();

        Category reloaded = categoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getCreatedBy()).isEqualTo("alice@example.com");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(createdAt.plus(Duration.ofMinutes(5)));
        assertThat(reloaded.getLastModifiedBy()).isEqualTo("bob@example.com");
    }

    private static void authenticateAs(String name) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(name, "n/a", "ROLE_CUSTOMER");
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
