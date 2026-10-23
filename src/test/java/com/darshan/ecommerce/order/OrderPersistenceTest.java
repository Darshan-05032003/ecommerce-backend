package com.darshan.ecommerce.order;

import com.darshan.ecommerce.category.Category;
import com.darshan.ecommerce.category.CategoryRepository;
import com.darshan.ecommerce.common.persistence.PersistenceTest;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PersistenceTest
class OrderPersistenceTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Category category;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("buyer@example.com", "{noop}irrelevant", "Buyer", Set.of()));
        category = categoryRepository.save(new Category("Electronics", null));
        laptop = productRepository.save(new Product("LAP-1", "Laptop", "Laptop", new BigDecimal("1000.00"), 5, category));
        mouse = productRepository.save(new Product("MOU-1", "Mouse", "Mouse", new BigDecimal("20.00"), 5, category));
        entityManager.flush();
    }

    @Test
    void cascadesPersistToOrderItems() {
        Order order = newOrder("ORD-2026-000001");

        orderRepository.saveAndFlush(order);

        assertThat(order.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
        assertThat(countItems(order)).isEqualTo(2);
    }

    @Test
    void deletesOrphanedItemsWhenRemovedFromTheOrder() {
        Order order = orderRepository.saveAndFlush(newOrder("ORD-2026-000002"));
        entityManager.clear();

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        reloaded.removeItem(reloaded.getItems().getFirst());
        orderRepository.saveAndFlush(reloaded);

        assertThat(countItems(reloaded)).isEqualTo(1);
    }

    @Test
    void deletesItemsTogetherWithTheOrder() {
        Order order = orderRepository.saveAndFlush(newOrder("ORD-2026-000003"));

        orderRepository.delete(order);
        entityManager.flush();

        assertThat(countItems(order)).isZero();
    }

    @Test
    void incrementsVersionOnEveryUpdate() {
        Order order = orderRepository.saveAndFlush(newOrder("ORD-2026-000004"));
        assertThat(order.getVersion()).isZero();

        order.changeStatus(OrderStatus.PAID);
        orderRepository.saveAndFlush(order);

        assertThat(order.getVersion()).isEqualTo(1);
    }

    @Test
    void storesTheLineTotalsAndTheirSumAsTheOrderTotal() {
        Order order = orderRepository.saveAndFlush(newOrder("ORD-2026-000005"));

        BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, order.getId());
        BigDecimal lines = jdbcTemplate.queryForObject(
                "SELECT SUM(line_total) FROM order_items WHERE order_id = ?", BigDecimal.class, order.getId());
        assertThat(total).isEqualTo(new BigDecimal("1020.00")).isEqualTo(lines);
    }

    @Test
    void rejectsAStatusOutsideTheLifecycle() {
        Order order = orderRepository.saveAndFlush(newOrder("ORD-2026-000006"));

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE orders SET status = 'LOST' WHERE id = ?", order.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_orders_status");
    }

    @Test
    void rejectsUpdateBasedOnStaleVersion() {
        Product saved = productRepository.saveAndFlush(
                new Product("STALE-1", "Stale", "Stale", new BigDecimal("10.00"), 1, category));
        entityManager.clear();

        Product firstAdmin = productRepository.findById(saved.getId()).orElseThrow();
        entityManager.clear();
        Product secondAdmin = productRepository.findById(saved.getId()).orElseThrow();
        secondAdmin.setPrice(new BigDecimal("12.00"));
        productRepository.saveAndFlush(secondAdmin);
        entityManager.clear();

        firstAdmin.setPrice(new BigDecimal("11.00"));
        assertThatThrownBy(() -> productRepository.saveAndFlush(firstAdmin))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private Order newOrder(String orderNumber) {
        Order order = new Order(orderNumber, user, "1 Main Street, Pune");
        order.addItem(new OrderItem(laptop, 1, laptop.getPrice()));
        order.addItem(new OrderItem(mouse, 1, mouse.getPrice()));
        return order;
    }

    private int countItems(Order order) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, order.getId());
        return count == null ? 0 : count;
    }
}
