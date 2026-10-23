package com.darshan.ecommerce.common.persistence;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.product.Product;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class EntityEqualityTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void unsavedEntitiesAreNeverEqualToEachOther() {
        Category first = new Category("Same", null);
        Category second = new Category("Same", null);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);
    }

    @Test
    void entityStaysFindableInHashSetAfterIdIsAssigned() {
        Category category = new Category("Set member", null);
        Set<Category> set = new HashSet<>();
        set.add(category);

        categoryRepository.saveAndFlush(category);

        assertThat(category.getId()).isNotNull();
        assertThat(set).contains(category);
    }

    @Test
    void managedEntityAndLazyProxyWithSameIdAreEqual() {
        Category saved = categoryRepository.saveAndFlush(new Category("Proxied", null));
        entityManager.clear();

        Category proxy = entityManager.getReference(Category.class, saved.getId());
        Category loaded = categoryRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded).isEqualTo(proxy);
        assertThat(proxy).isEqualTo(loaded);
        assertThat(loaded.hashCode()).isEqualTo(proxy.hashCode());
    }

    @Test
    void differentEntityTypesWithSameIdAreNotEqual() {
        Category category = new Category("Typed", null);
        Product product = new Product("SKU-EQ", "Product", "Description", BigDecimal.ONE, 1);
        ReflectionTestUtils.setField(category, "id", 42L);
        ReflectionTestUtils.setField(product, "id", 42L);

        assertThat(category).isNotEqualTo(product);
        assertThat(category.hashCode()).isNotEqualTo(product.hashCode());
    }
}
