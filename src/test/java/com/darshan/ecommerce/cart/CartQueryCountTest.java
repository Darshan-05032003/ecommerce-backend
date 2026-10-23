package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.IntegrationTestSupport.TestApi;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.user.UserRepository;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Each product sits in its own category and each user has a different number of lines, so a lazy
// product load per line would show up as one extra SELECT per line.
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CartQueryCountTest {

    @Autowired
    private TestApi api;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final Map<Integer, Long> userIdByLineCount = new HashMap<>();

    @BeforeAll
    void createCartsOfDifferentSizes() throws Exception {
        api.cleanDatabase();
        for (int lines : new int[] {1, 4, 8}) {
            Long userId = api.registerCustomer("lines" + lines + "@example.com", "Str0ng!pw");
            userIdByLineCount.put(lines, userId);
            for (int i = 1; i <= lines; i++) {
                String suffix = lines + "-" + i;
                Product product = api.createProduct("CQ-" + suffix, new BigDecimal("2.50"), 20,
                        api.createCategory("Cart query " + suffix));
                cartItemRepository.save(new CartItem(userRepository.getReferenceById(userId), product, 1));
            }
        }
    }

    @ParameterizedTest(name = "{0} lines")
    @ValueSource(ints = {1, 4, 8})
    void loadingACartUsesOneStatementWhateverTheNumberOfLines(int lines) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        CartResponse cart = cartService.getCart(userIdByLineCount.get(lines));

        assertThat(cart.lines()).hasSize(lines);
        assertThat(cart.total()).isEqualByComparingTo(new BigDecimal("2.50").multiply(BigDecimal.valueOf(lines)));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
