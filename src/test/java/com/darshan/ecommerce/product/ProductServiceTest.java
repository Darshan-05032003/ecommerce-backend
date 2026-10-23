package com.darshan.ecommerce.product;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.common.exception.ConflictException;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private ProductService productService;

    private Category books;
    private Category electronics;
    private Product product;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, new ProductMapperImpl());
        books = category(1L, "Books");
        electronics = category(2L, "Electronics");
        product = aProduct().id(10L).sku("BK-JAVA-001").name("Effective Java").price("2800.00").stock(40)
                .category(books).build();
        ReflectionTestUtils.setField(product, "version", 3);
    }

    @Test
    void getActiveByIdMapsTheProductAndItsCategory() {
        when(productRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getActiveById(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sku()).isEqualTo("BK-JAVA-001");
        assertThat(response.price()).isEqualByComparingTo("2800.00");
        assertThat(response.version()).isEqualTo(3);
        assertThat(response.category()).isEqualTo(new ProductResponse.CategorySummary(1L, "Books"));
    }

    @Test
    void getActiveByIdRejectsUnknownOrInactiveProducts() {
        when(productRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getActiveById(10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id 10");
    }

    @Test
    void createResolvesTheCategoryAndSavesAnActiveProduct() {
        when(productRepository.existsBySku("LAP-14-001")).thenReturn(false);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(electronics));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.create(new CreateProductRequest(
                "LAP-14-001", "Laptop", "14 inch", new BigDecimal("75000.00"), 10, 2L));

        assertThat(response.active()).isTrue();
        assertThat(response.category().name()).isEqualTo("Electronics");
        assertThat(response.stockQuantity()).isEqualTo(10);
    }

    @Test
    void createRejectsADuplicateSku() {
        when(productRepository.existsBySku("BK-JAVA-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(new CreateProductRequest(
                "BK-JAVA-001", "Copy", "Copy", BigDecimal.ONE, 1, 1L)))
                .isInstanceOf(ConflictException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsAnUnknownCategory() {
        when(productRepository.existsBySku("LAP-14-001")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(new CreateProductRequest(
                "LAP-14-001", "Laptop", "14 inch", BigDecimal.TEN, 1, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found with id 99");
    }

    @Test
    void updateWithTheCurrentVersionAppliesEveryFieldAndMovesTheCategory() {
        when(productRepository.findWithCategoryById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(electronics));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        ProductResponse response = productService.update(10L, new UpdateProductRequest(
                "BK-JAVA-001", "Effective Java 3e", "Updated", new BigDecimal("2500.00"), 35, 2L, false, 3));

        assertThat(response.name()).isEqualTo("Effective Java 3e");
        assertThat(response.price()).isEqualByComparingTo("2500.00");
        assertThat(response.stockQuantity()).isEqualTo(35);
        assertThat(response.active()).isFalse();
        assertThat(response.category().id()).isEqualTo(2L);
    }

    @Test
    void updateWithAStaleVersionIsRejectedBeforeAnythingIsWritten() {
        when(productRepository.findWithCategoryById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.update(10L, new UpdateProductRequest(
                "BK-JAVA-001", "Effective Java", "Third edition", new BigDecimal("2800.00"), 40, 1L, true, 2)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(product.getName()).isEqualTo("Effective Java");
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteDeactivatesInsteadOfRemovingTheRow() {
        when(productRepository.findWithCategoryById(10L)).thenReturn(Optional.of(product));

        productService.delete(10L);

        assertThat(product.isActive()).isFalse();
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void reserveStockSucceedsWhenTheConditionalUpdateChangedTheRow() {
        when(productRepository.decreaseStockIfAvailable(10L, 3)).thenReturn(1);

        productService.reserveStock(10L, 3);

        verify(productRepository, never()).findActiveFlagById(any());
    }

    @Test
    void reserveStockReportsOutOfStockForAnActiveProduct() {
        when(productRepository.decreaseStockIfAvailable(10L, 41)).thenReturn(0);
        when(productRepository.findActiveFlagById(10L)).thenReturn(Optional.of(true));

        assertThatThrownBy(() -> productService.reserveStock(10L, 41))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo(ProductService.OUT_OF_STOCK);
    }

    @Test
    void reserveStockReportsAWithdrawnProductAsUnavailable() {
        when(productRepository.decreaseStockIfAvailable(10L, 1)).thenReturn(0);
        when(productRepository.findActiveFlagById(10L)).thenReturn(Optional.of(false));

        assertThatThrownBy(() -> productService.reserveStock(10L, 1))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo(ProductService.PRODUCT_UNAVAILABLE);
    }

    @Test
    void reserveStockReportsAnUnknownProductAsNotFound() {
        when(productRepository.decreaseStockIfAvailable(99L, 1)).thenReturn(0);
        when(productRepository.findActiveFlagById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.reserveStock(99L, 1)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void releaseStockRejectsAnUnknownProduct() {
        when(productRepository.increaseStock(99L, 2)).thenReturn(0);

        assertThatThrownBy(() -> productService.releaseStock(99L, 2)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static Category category(Long id, String name) {
        Category category = new Category(name, null);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
