package com.darshan.ecommerce.product;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.common.config.CacheNames;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ConflictException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;
import com.darshan.ecommerce.common.observability.LogExecutionTime;
import com.darshan.ecommerce.common.web.PageResponse;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String PRODUCT_UNAVAILABLE = "PRODUCT_UNAVAILABLE";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          ProductMapper mapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    // Only active products reach the cache: a missing or soft-deleted product throws, and Spring
    // Cache never stores a method call that ended in an exception.
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse getActiveById(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    // Pages are not cached: every combination of filters, sort and page would be its own entry, hits
    // would be rare, and one product change would make an unknown number of pages stale.
    @LogExecutionTime
    public PageResponse<ProductResponse> listActive(ProductFilter filter, Pageable pageable) {
        return page(filter.toSpecification().and(ProductSpecifications.active()), pageable);
    }

    public PageResponse<ProductResponse> listAll(ProductFilter filter, Pageable pageable) {
        return page(filter.toSpecification(), pageable);
    }

    public ProductResponse getById(Long id) {
        return mapper.toResponse(load(id));
    }

    // The existence check gives a clean 409; the unique index on sku is what makes it safe when two
    // admins create the same SKU at once, and the handler maps that violation to 409 as well.
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        requireSkuAvailable(request.sku());
        Product product = new Product(request.sku(), request.name(), request.description(), request.price(),
                request.stockQuantity(), category(request.categoryId()));
        return mapper.toResponse(productRepository.save(product));
    }

    // The cache holds what the public endpoint may return, so an update that leaves the product
    // active replaces the entry and one that deactivates it removes the entry.
    @Transactional
    @Caching(
            put = @CachePut(cacheNames = CacheNames.PRODUCTS, key = "#id", unless = "!#result.active()"),
            evict = @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id", condition = "!#result.active()"))
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = load(id);
        if (!request.version().equals(product.getVersion())) {
            throw new OptimisticLockingFailureException("Product " + id + " was modified after version "
                    + request.version() + " was read");
        }
        if (!product.getSku().equals(request.sku())) {
            requireSkuAvailable(request.sku());
        }
        mapper.applyUpdate(request, product);
        product.setCategory(category(request.categoryId()));
        // Flushing now runs the versioned UPDATE inside this method: a concurrent change that slipped
        // past the check above surfaces here as a 409, and the response carries the new version.
        return mapper.toResponse(productRepository.saveAndFlush(product));
    }

    // Soft delete: sold products are referenced by order lines and must stay for history, so the row
    // stays and only disappears from the public catalogue. Deleting twice is a no-op.
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public void delete(Long id) {
        load(id).setActive(false);
    }

    // MANDATORY: reserving stock only makes sense as part of a larger unit (placing an order) that
    // must roll the decrement back if anything after it fails. Called without a transaction this
    // fails fast instead of committing the decrement on its own. The cache is transaction aware, so
    // the eviction happens after commit and not at all on rollback.
    @Transactional(propagation = Propagation.MANDATORY)
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public void reserveStock(Long productId, int quantity) {
        if (productRepository.decreaseStockIfAvailable(productId, quantity) == 0) {
            throw reservationFailure(productId);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public void releaseStock(Long productId, int quantity) {
        if (productRepository.increaseStock(productId, quantity) == 0) {
            throw notFound(productId);
        }
    }

    // Zero updated rows has three possible causes; the extra query runs only on this failure path.
    private BusinessRuleViolationException reservationFailure(Long productId) {
        boolean active = productRepository.findActiveFlagById(productId).orElseThrow(() -> notFound(productId));
        if (!active) {
            return new BusinessRuleViolationException(PRODUCT_UNAVAILABLE,
                    "Product " + productId + " is no longer available");
        }
        return new BusinessRuleViolationException(OUT_OF_STOCK, "Not enough stock left for product " + productId);
    }

    private PageResponse<ProductResponse> page(Specification<Product> specification, Pageable pageable) {
        return PageResponse.from(productRepository.findAll(specification, pageable).map(mapper::toResponse));
    }

    private Product load(Long id) {
        return productRepository.findWithCategoryById(id).orElseThrow(() -> notFound(id));
    }

    private Category category(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + categoryId));
    }

    private void requireSkuAvailable(String sku) {
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("SKU already in use");
        }
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Product not found with id " + id);
    }
}
