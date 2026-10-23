package com.darshan.ecommerce.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Listing pages in two steps: first a page of ids (LIMIT and the count run in SQL), then the
// orders with their items and products for exactly those ids. A fetch join of the items
// collection in the paged query itself would make Hibernate load every matching row and page in
// memory (warning HHH90003004), because joined item rows break LIMIT.
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query(value = "select o.id from Order o where o.user.id = :userId",
            countQuery = "select count(o) from Order o where o.user.id = :userId")
    Page<Long> findIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "select o.id from Order o where :status is null or o.status = :status",
            countQuery = "select count(o) from Order o where :status is null or o.status = :status")
    Page<Long> findIdsByStatus(@Param("status") OrderStatus status, Pageable pageable);

    @Query("""
            select o from Order o left join fetch o.items i left join fetch i.product
            where o.id in :ids""")
    List<Order> findWithItemsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select o from Order o left join fetch o.items i left join fetch i.product
            where o.id = :id""")
    Optional<Order> findWithItemsById(@Param("id") Long id);

    @Query("""
            select o from Order o left join fetch o.items i left join fetch i.product
            where o.id = :id and o.user.id = :userId""")
    Optional<Order> findWithItemsByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
