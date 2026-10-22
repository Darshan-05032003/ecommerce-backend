package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.user.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUser(User user);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUser(User user);
}

