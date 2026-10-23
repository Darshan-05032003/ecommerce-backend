package com.darshan.ecommerce.product;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.common.config.CacheNames;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs the stock updates on MySQL, because what matters here is the SQL the conditional update
// produces and how the row count, the version column and the transaction-aware cache behave.
@IntegrationTest
class ProductStockIntegrationTest {

    @Autowired
    private TestApi api;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private Product product;

    @BeforeEach
    void setUp() {
        api.cleanCatalogue();
        transaction = new TransactionTemplate(transactionManager);
        product = api.createProduct("STK-001", 5);
    }

    @Test
    void reservingDecrementsTheStockAndBumpsTheVersion() {
        transaction.executeWithoutResult(status -> productService.reserveStock(product.getId(), 3));

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(2);
        assertThat(reloaded.getVersion()).isEqualTo(product.getVersion() + 1);
    }

    @Test
    void reservingTheExactRemainingStockLeavesZero() {
        transaction.executeWithoutResult(status -> productService.reserveStock(product.getId(), 5));

        assertThat(stockOf(product)).isZero();
    }

    @Test
    void reservingMoreThanTheStockChangesNothing() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                productService.reserveStock(product.getId(), 6)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo(ProductService.OUT_OF_STOCK);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(5);
        assertThat(reloaded.getVersion()).isEqualTo(product.getVersion());
    }

    @Test
    void aWithdrawnProductCannotBeReservedEvenWithStock() {
        product.setActive(false);
        productRepository.save(product);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                productService.reserveStock(product.getId(), 1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo(ProductService.PRODUCT_UNAVAILABLE);
        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void stockCannotBeReservedOutsideATransaction() {
        assertThatThrownBy(() -> productService.reserveStock(product.getId(), 1))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void releasingAddsTheQuantityBack() {
        transaction.executeWithoutResult(status -> productService.releaseStock(product.getId(), 4));

        assertThat(stockOf(product)).isEqualTo(9);
    }

    @Test
    void theCachedProductIsEvictedOnlyWhenTheReservationCommits() {
        productService.getActiveById(product.getId());
        Cache cache = cacheManager.getCache(CacheNames.PRODUCTS);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                productService.reserveStock(product.getId(), 50)))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(cache.get(product.getId())).isNotNull();

        transaction.executeWithoutResult(status -> {
            productService.reserveStock(product.getId(), 1);
            assertThat(cache.get(product.getId())).as("still cached before commit").isNotNull();
        });
        assertThat(cache.get(product.getId())).isNull();
    }

    private int stockOf(Product p) {
        return productRepository.findById(p.getId()).orElseThrow().getStockQuantity();
    }
}
