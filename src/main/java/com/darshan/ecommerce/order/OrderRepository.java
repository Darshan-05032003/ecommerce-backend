package com.darshan.ecommerce.order;

import com.darshan.ecommerce.user.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser(User user);
    
    Page<Order> findByUser(User user, Pageable pageable);
}

