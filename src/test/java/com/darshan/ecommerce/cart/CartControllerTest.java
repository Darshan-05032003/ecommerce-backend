package com.darshan.ecommerce.cart;

import com.darshan.ecommerce.common.config.ClockConfig;
import com.darshan.ecommerce.common.exception.BusinessRuleViolationException;
import com.darshan.ecommerce.common.exception.ProblemDetailFactory;
import com.darshan.ecommerce.common.security.UserPrincipal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The application's SecurityConfig is not part of the slice, so Boot's default chain applies:
// requests carry a CSRF token, and the principal is the same UserPrincipal type the JWT filter sets.
@WebMvcTest(controllers = CartController.class)
@Import({ClockConfig.class, ProblemDetailFactory.class})
class CartControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    void addsAnItemForTheAuthenticatedUser() throws Exception {
        when(cartService.addItem(eq(USER_ID), any())).thenReturn(new CartResponse(
                List.of(new CartLineResponse(7L, "BK-001", "Effective Java", new BigDecimal("19.99"), 2,
                        new BigDecimal("39.98"))), 2, new BigDecimal("39.98")));

        mockMvc.perform(post("/api/v1/me/cart/items").with(csrf()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":7,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].sku").value("BK-001"))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(39.98))
                .andExpect(jsonPath("$.total").value(39.98));

        verify(cartService).addItem(USER_ID, new AddCartItemRequest(7L, 2));
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"productId\":7,\"quantity\":0}   | quantity  | must be greater than or equal to 1",
            "{\"productId\":7,\"quantity\":11}  | quantity  | must be less than or equal to 10",
            "{\"productId\":7}                  | quantity  | must not be null",
            "{\"quantity\":1}                   | productId | must not be null",
            "{\"productId\":0,\"quantity\":1}   | productId | must be greater than 0",
            "{\"productId\":-3,\"quantity\":1}  | productId | must be greater than 0"
    })
    void rejectsAnInvalidAddRequest(String body, String field, String message) throws Exception {
        mockMvc.perform(post("/api/v1/me/cart/items").with(csrf()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(message));

        verifyNoInteractions(cartService);
    }

    @ParameterizedTest(name = "quantity {0}")
    @ValueSource(ints = {0, 11, -1})
    void rejectsAnUpdateOutsideOneToTen(int quantity) throws Exception {
        mockMvc.perform(patch("/api/v1/me/cart/items/7").with(csrf()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        verifyNoInteractions(cartService);
    }

    @Test
    void theOldQueryParameterFormIsNoLongerAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/me/cart/items").with(csrf()).with(customer())
                        .param("productId", "7").param("quantity", "500"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    void aMalformedBodyIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/me/cart/items").with(csrf()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":7,\"quantity\":\"many\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    void aBrokenBusinessRuleIs422WithItsErrorCode() throws Exception {
        when(cartService.updateQuantity(USER_ID, 7L, 9)).thenThrow(new BusinessRuleViolationException(
                "INSUFFICIENT_STOCK", "Only 3 units of product 7 are in stock"));

        mockMvc.perform(patch("/api/v1/me/cart/items/7").with(csrf()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":9}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.detail").value("Only 3 units of product 7 are in stock"));
    }

    private static RequestPostProcessor customer() {
        return user(new UserPrincipal(USER_ID, "alice@example.com", "hash",
                Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }
}
