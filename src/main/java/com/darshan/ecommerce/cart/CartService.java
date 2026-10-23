package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.exception.ResourceNotFoundException;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    static final int MAX_ADD_ATTEMPTS = 3;

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public CartService(CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository,
                       PlatformTransactionManager transactionManager) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return loadCart(userId);
    }

    // Two requests adding the same product to an empty cart (a double click) both see no line and
    // both insert; the unique key on (user_id, product_id) lets one win and the other fail. The
    // loser cannot recover inside its own transaction, which is already marked for rollback, so
    // the whole unit is run again in a new transaction where it finds the line and merges into it.
    // That is why this method is not @Transactional and uses a TransactionTemplate per attempt.
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status ->
                        addItemOnce(userId, request.productId(), request.quantity()));
            } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
                if (attempt == MAX_ADD_ATTEMPTS) {
                    throw ex;
                }
                log.info("Retrying add to cart for user {} after a concurrent change ({})", userId,
                        ex.getClass().getSimpleName());
            }
        }
    }

    @Transactional
    public CartResponse updateQuantity(Long userId, Long productId, int quantity) {
        CartItem line = cartItemRepository.findLineForUpdate(userId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in the cart"));
        CartRules.checkLine(line.getProduct(), quantity);
        line.changeQuantity(quantity);
        return loadCart(userId);
    }

    @Transactional
    public void removeLine(Long userId, Long productId) {
        cartItemRepository.deleteLine(userId, productId);
    }

    @Transactional
    public void clear(Long userId) {
        cartItemRepository.deleteAllForUser(userId);
    }

    private CartResponse addItemOnce(Long userId, Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + productId));

        Optional<CartItem> existing = cartItemRepository.findLineForUpdate(userId, productId);
        if (existing.isPresent()) {
            CartItem line = existing.get();
            int merged = line.getQuantity() + quantity;
            CartRules.checkLine(product, merged);
            line.changeQuantity(merged);
        } else {
            CartRules.checkLine(product, quantity);
            cartItemRepository.saveAndFlush(new CartItem(userRepository.getReferenceById(userId), product, quantity));
        }
        return loadCart(userId);
    }

    private CartResponse loadCart(Long userId) {
        return CartResponse.of(cartItemRepository.findCartWithProducts(userId).stream()
                .map(CartLineResponse::from)
                .toList());
    }
}
