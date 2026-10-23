package com.darshan.ecommerce.product;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.common.persistence.PersistenceTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class ProductSpecificationsTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category books;
    private Category electronics;

    @BeforeEach
    void createCatalogue() {
        books = categoryRepository.save(new Category("Spec Books", null));
        electronics = categoryRepository.save(new Category("Spec Electronics", null));
        product("BK-JAVA-001", "Effective Java", "45.00", 10, books);
        product("BK-SOLD-001", "Java Concurrency in Practice", "55.00", 0, books);
        product("BK-DDIA-001", "Designing Data-Intensive Applications", "60.00", 3, books);
        product("LAP-14-001", "Laptop 14", "750.00", 2, electronics);
        product("MOU-50-001", "Mouse 50% off", "15.00", 8, electronics);
        Product hidden = product("BK-OLD-001", "Java 1.4 Handbook", "5.00", 4, books);
        hidden.setActive(false);
    }

    @Test
    void emptyFilterReturnsEveryActiveProduct() {
        assertThat(skus(new ProductFilter(null, null, null, null, null)))
                .containsExactly("BK-DDIA-001", "BK-JAVA-001", "BK-SOLD-001", "LAP-14-001", "MOU-50-001");
    }

    @Test
    void filtersByCategory() {
        assertThat(skus(new ProductFilter(electronics.getId(), null, null, null, null)))
                .containsExactly("LAP-14-001", "MOU-50-001");
    }

    @Test
    void priceBoundsAreInclusive() {
        assertThat(skus(new ProductFilter(null, new BigDecimal("45.00"), null, null, null)))
                .containsExactly("BK-DDIA-001", "BK-JAVA-001", "BK-SOLD-001", "LAP-14-001");
        assertThat(skus(new ProductFilter(null, null, new BigDecimal("55.00"), null, null)))
                .containsExactly("BK-JAVA-001", "BK-SOLD-001", "MOU-50-001");
    }

    @Test
    void nameSearchIsCaseInsensitiveAndMatchesAnywhereInTheName() {
        assertThat(skus(new ProductFilter(null, null, null, "jAvA", null)))
                .containsExactly("BK-JAVA-001", "BK-SOLD-001");
        assertThat(skus(new ProductFilter(null, null, null, "  data-intensive ", null)))
                .containsExactly("BK-DDIA-001");
    }

    @Test
    void likeWildcardsInTheSearchTextAreMatchedLiterally() {
        assertThat(skus(new ProductFilter(null, null, null, "50%", null))).containsExactly("MOU-50-001");
        assertThat(skus(new ProductFilter(null, null, null, "%", null))).containsExactly("MOU-50-001");
        assertThat(skus(new ProductFilter(null, null, null, "_", null))).isEmpty();
    }

    @Test
    void inStockExcludesSoldOutProductsAndFalseMeansNoFilter() {
        assertThat(skus(new ProductFilter(null, null, null, null, true)))
                .containsExactly("BK-DDIA-001", "BK-JAVA-001", "LAP-14-001", "MOU-50-001");
        assertThat(skus(new ProductFilter(null, null, null, null, false))).hasSize(5);
    }

    @Test
    void combinedFiltersAreAndedTogether() {
        ProductFilter filter = new ProductFilter(books.getId(), new BigDecimal("40.00"), new BigDecimal("58.00"),
                "java", true);

        assertThat(skus(filter)).containsExactly("BK-JAVA-001");
    }

    @Test
    void softDeletedProductsAreExcludedEvenWhenEveryFilterMatches() {
        ProductFilter filter = new ProductFilter(books.getId(), new BigDecimal("1.00"), new BigDecimal("10.00"),
                "handbook", true);

        assertThat(skus(filter)).isEmpty();
        assertThat(productRepository.findAll(filter.toSpecification())).extracting(Product::getSku)
                .containsExactly("BK-OLD-001");
    }

    private List<String> skus(ProductFilter filter) {
        Specification<Product> specification = filter.toSpecification().and(ProductSpecifications.active());
        return productRepository.findAll(specification, PageRequest.of(0, 20, Sort.by("sku")))
                .map(Product::getSku)
                .getContent();
    }

    private Product product(String sku, String name, String price, int stock, Category category) {
        return productRepository.save(new Product(sku, name, "Description", new BigDecimal(price), stock, category));
    }
}
