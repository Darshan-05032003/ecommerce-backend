package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.user.User;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUser(User user);

    // Every line is shown with its product's sku, name and price, so the product is joined into
    // the same SELECT instead of being loaded lazily once per line.
    @Query("""
            select ci from CartItem ci join fetch ci.product
            where ci.user.id = :userId
            order by ci.id""")
    List<CartItem> findCartWithProducts(@Param("userId") Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    // SELECT ... FOR UPDATE: two requests adding to the same line wait for each other instead of
    // both reading the old quantity and the second overwriting the first one's increment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ci from CartItem ci where ci.user.id = :userId and ci.product.id = :productId")
    Optional<CartItem> findLineForUpdate(@Param("userId") Long userId, @Param("productId") Long productId);

    @Modifying
    @Query("delete from CartItem ci where ci.user.id = :userId and ci.product.id = :productId")
    int deleteLine(@Param("userId") Long userId, @Param("productId") Long productId);

    @Modifying
    @Query("delete from CartItem ci where ci.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    void deleteByUser(User user);
}
