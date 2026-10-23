package com.darshan.ecommerce.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    // Every listed product is mapped together with its category name; the entity graph turns the
    // page query into one LEFT JOIN instead of one extra SELECT per category on the page.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    boolean existsBySku(String sku);

    // Check and decrement in one statement. InnoDB evaluates the WHERE clause of an UPDATE against
    // the latest committed row while holding its row lock, so two buyers can never both pass the
    // stock check on the same value. The version is bumped by hand because a bulk update bypasses
    // Hibernate's @Version handling, and an admin edit based on the old stock must get a 409.
    @Modifying
    @Query("""
            update Product p
            set p.stockQuantity = p.stockQuantity - :quantity, p.version = p.version + 1
            where p.id = :id and p.stockQuantity >= :quantity and p.active = true""")
    int decreaseStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("""
            update Product p
            set p.stockQuantity = p.stockQuantity + :quantity, p.version = p.version + 1
            where p.id = :id""")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Query("select p.active from Product p where p.id = :id")
    Optional<Boolean> findActiveFlagById(@Param("id") Long id);
}
