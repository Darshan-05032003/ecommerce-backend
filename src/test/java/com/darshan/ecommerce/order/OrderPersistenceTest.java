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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;

import static com.darshan.ecommerce.order.OrderTestData.anOrder;
import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static com.darshan.ecommerce.user.UserTestData.aUser;
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
        user = userRepository.save(aUser().build());
        category = categoryRepository.save(new Category("Electronics", null));
        laptop = productRepository.save(aProduct().name("Laptop").price("1000.00").category(category).build());
        mouse = productRepository.save(aProduct().name("Mouse").price("20.00").category(category).build());
        entityManager.flush();
    }

    @Test
    void cascadesPersistToOrderItems() {
        Order order = newOrder();

        orderRepository.saveAndFlush(order);

        assertThat(order.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
        assertThat(countItems(order)).isEqualTo(2);
    }

    @Test
    void deletesOrphanedItemsWhenRemovedFromTheOrder() {
        Order order = orderRepository.saveAndFlush(newOrder());
        entityManager.clear();

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        reloaded.removeItem(reloaded.getItems().getFirst());
        orderRepository.saveAndFlush(reloaded);

        assertThat(countItems(reloaded)).isEqualTo(1);
    }

    @Test
    void deletesItemsTogetherWithTheOrder() {
        Order order = orderRepository.saveAndFlush(newOrder());

        orderRepository.delete(order);
        entityManager.flush();

        assertThat(countItems(order)).isZero();
    }

    @Test
    void incrementsVersionOnEveryUpdate() {
        Order order = orderRepository.saveAndFlush(newOrder());
        assertThat(order.getVersion()).isZero();

        order.changeStatus(OrderStatus.PAID);
        orderRepository.saveAndFlush(order);

        assertThat(order.getVersion()).isEqualTo(1);
    }

    @Test
    void storesTheLineTotalsAndTheirSumAsTheOrderTotal() {
        Order order = orderRepository.saveAndFlush(newOrder());

        BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, order.getId());
        BigDecimal lines = jdbcTemplate.queryForObject(
                "SELECT SUM(line_total) FROM order_items WHERE order_id = ?", BigDecimal.class, order.getId());
        assertThat(total).isEqualTo(new BigDecimal("1020.00")).isEqualTo(lines);
    }

    @Test
    void rejectsAStatusOutsideTheLifecycle() {
        Order order = orderRepository.saveAndFlush(newOrder());

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE orders SET status = 'LOST' WHERE id = ?", order.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_orders_status");
    }

    @Test
    void rejectsUpdateBasedOnStaleVersion() {
        Product saved = productRepository.saveAndFlush(aProduct().price("10.00").category(category).build());
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

    @Test
    void findsAnOrderWithItsLinesOnlyForItsOwner() {
        Order order = orderRepository.saveAndFlush(newOrder());
        User otherUser = userRepository.saveAndFlush(aUser().build());
        entityManager.clear();

        assertThat(orderRepository.findWithItemsByIdAndUserId(order.getId(), user.getId()))
                .hasValueSatisfying(found -> assertThat(found.getItems()).hasSize(2));
        assertThat(orderRepository.findWithItemsByIdAndUserId(order.getId(), otherUser.getId())).isEmpty();
    }

    @Test
    void filtersOrderIdsByStatusOrReturnsAllWithoutAFilter() {
        Order created = orderRepository.save(newOrder());
        Order shipped = orderRepository.save(
                anOrder().user(user).line(laptop, 1).status(OrderStatus.SHIPPED).build());
        orderRepository.flush();
        PageRequest page = PageRequest.of(0, 10, Sort.by("id"));

        assertThat(orderRepository.findIdsByStatus(OrderStatus.SHIPPED, page).getContent())
                .containsExactly(shipped.getId());
        assertThat(orderRepository.findIdsByStatus(null, page).getContent())
                .containsExactly(created.getId(), shipped.getId());
    }

    private Order newOrder() {
        return anOrder().user(user).line(laptop, 1).line(mouse, 1).build();
    }

    private int countItems(Order order) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, order.getId());
        return count == null ? 0 : count;
    }
}
