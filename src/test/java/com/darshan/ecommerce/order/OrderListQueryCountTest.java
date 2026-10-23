package com.darshan.ecommerce.order;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.cart.CartItem;
import com.darshan.ecommerce.cart.CartItemRepository;
import com.darshan.ecommerce.common.web.PageResponse;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.user.UserRepository;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Every order has two lines and every product sits in its own category, so loading items or
// products lazily would show up as extra statements that grow with the page size.
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderListQueryCountTest {

    private static final int ORDERS = 12;

    @Autowired
    private TestApi api;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderPlacementService placementService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long userId;
    private Long anyOrderId;

    @BeforeAll
    void placeOrders() throws Exception {
        api.cleanDatabase();
        userId = api.registerCustomer("lister@example.com", "Str0ng!pw");
        for (int i = 1; i <= ORDERS; i++) {
            for (int line = 1; line <= 2; line++) {
                String suffix = i + "-" + line;
                Product product = api.createProduct("OQ-" + suffix, new BigDecimal("3.10"), 5,
                        api.createCategory("Order query " + suffix));
                cartItemRepository.save(new CartItem(userRepository.getReferenceById(userId), product, 1));
            }
            anyOrderId = placementService.place(userId, UUID.randomUUID().toString(),
                    new PlaceOrderRequest("1 Main Street")).order().id();
        }
    }

    @ParameterizedTest(name = "page size {0}")
    @ValueSource(ints = {2, 5, 10})
    void listingAPageUsesThreeStatementsWhateverItsSize(int size) {
        Statistics statistics = statistics();

        PageResponse<OrderResponse> page = orderService.listForUser(userId,
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));

        assertThat(page.content()).hasSize(size).allSatisfy(order -> assertThat(order.items()).hasSize(2));
        assertThat(page.totalElements()).isEqualTo(ORDERS);
        // the page of ids, the count, and the orders with their items and products
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void theDetailViewUsesTwoStatements() {
        Statistics statistics = statistics();

        OrderDetailResponse detail = orderService.getForUser(userId, anyOrderId);

        assertThat(detail.items()).hasSize(2);
        assertThat(detail.history()).hasSize(1);
        // the order with its items and products, and the status history
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}
