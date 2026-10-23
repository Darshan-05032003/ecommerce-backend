package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ResourceNotFoundException;
import com.darshan.ecommerce.product.Product;
import com.darshan.ecommerce.product.ProductRepository;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.darshan.ecommerce.product.ProductTestData.aProduct;
import static com.darshan.ecommerce.user.UserTestData.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CartService cartService;
    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartItemRepository, productRepository, userRepository, transactionManager);
        user = aUser().build();
        product = aProduct().id(PRODUCT_ID).price("19.99").stock(20).build();
    }

    @Test
    void addingANewProductCreatesALine() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(cartItemRepository.findCartWithProducts(USER_ID)).thenReturn(List.of(new CartItem(user, product, 3)));

        CartResponse cart = cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 3));

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(3);
        assertThat(saved.getValue().getProduct()).isSameAs(product);
        assertThat(cart.total()).isEqualTo(new BigDecimal("59.97"));
    }

    @Test
    void addingAProductAlreadyInTheCartIncreasesItsQuantity() {
        CartItem line = new CartItem(user, product, 3);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(line));
        when(cartItemRepository.findCartWithProducts(USER_ID)).thenReturn(List.of(line));

        CartResponse cart = cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 4));

        assertThat(line.getQuantity()).isEqualTo(7);
        assertThat(cart.lines()).singleElement().extracting(CartLineResponse::quantity).isEqualTo(7);
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void aMergeAboveTenIsRejectedAndTheLineIsLeftUnchanged() {
        CartItem line = new CartItem(user, product, 8);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 3)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("CART_LINE_LIMIT_EXCEEDED");
        assertThat(line.getQuantity()).isEqualTo(8);
        verify(transactionManager).rollback(isNull());
    }

    @Test
    void anUnknownProductIsNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id 10");
    }

    @Test
    void anInactiveProductCannotBeAdded() {
        product.setActive(false);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("PRODUCT_UNAVAILABLE");
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void aLostInsertRaceIsRetriedAndMergedIntoTheWinnersLine() {
        CartItem winnersLine = new CartItem(user, product, 2);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnersLine));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(cartItemRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(cartItemRepository.findCartWithProducts(USER_ID)).thenReturn(List.of(winnersLine));

        CartResponse cart = cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 1));

        assertThat(winnersLine.getQuantity()).isEqualTo(3);
        assertThat(cart.totalQuantity()).isEqualTo(3);
        verify(transactionManager).rollback(isNull());
        verify(transactionManager).commit(isNull());
    }

    @Test
    void givesUpAfterThreeFailedAttempts() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(cartItemRepository.saveAndFlush(any())).thenThrow(new CannotAcquireLockException("deadlock"));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 1)))
                .isInstanceOf(CannotAcquireLockException.class);
        verify(cartItemRepository, times(CartService.MAX_ADD_ATTEMPTS)).saveAndFlush(any());
    }

    @Test
    void businessRuleViolationsAreNotRetried() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 21)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(productRepository, times(1)).findById(PRODUCT_ID);
    }

    @Test
    void updatingSetsTheQuantityAfterCheckingTheStock() {
        CartItem line = new CartItem(user, product, 2);
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(line));
        when(cartItemRepository.findCartWithProducts(USER_ID)).thenReturn(List.of(line));

        CartResponse cart = cartService.updateQuantity(USER_ID, PRODUCT_ID, 5);

        assertThat(line.getQuantity()).isEqualTo(5);
        assertThat(cart.total()).isEqualTo(new BigDecimal("99.95"));
    }

    @Test
    void updatingAboveTheStockIsRejected() {
        product.setStockQuantity(4);
        CartItem line = new CartItem(user, product, 2);
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, PRODUCT_ID, 5))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo("INSUFFICIENT_STOCK");
        assertThat(line.getQuantity()).isEqualTo(2);
    }

    @Test
    void updatingAProductThatIsNotInTheCartIsNotFound() {
        when(cartItemRepository.findLineForUpdate(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, PRODUCT_ID, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product 10 is not in the cart");
    }
}
