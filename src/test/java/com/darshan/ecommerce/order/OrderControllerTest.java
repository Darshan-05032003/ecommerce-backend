package com.darshan.ecommerce.order;

import com.darshan.ecommerce.common.config.ClockConfig;
import com.darshan.ecommerce.common.exception.InvalidRequestException;
import com.darshan.ecommerce.common.exception.ProblemDetailFactory;
import com.darshan.ecommerce.common.security.UserPrincipal;
import com.darshan.ecommerce.common.web.PageResponse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({ClockConfig.class, ProblemDetailFactory.class})
class OrderControllerTest {

    private static final Long USER_ID = 42L;
    private static final PlaceOrderRequest REQUEST = new PlaceOrderRequest("1 Main Street, Pune");
    private static final OrderResponse ORDER = new OrderResponse(17L, "ORD-2026-000017", USER_ID, OrderStatus.CREATED,
            new BigDecimal("40.00"), "1 Main Street, Pune", null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderPlacementService placementService;

    @Test
    void aNewOrderIs201WithItsLocation() throws Exception {
        when(placementService.place(USER_ID, "checkout-1", REQUEST)).thenReturn(new PlacementResult.Created(ORDER));

        placeOrder("checkout-1")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/me/orders/17"))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.orderNumber").value("ORD-2026-000017"));
    }

    @Test
    void aReplayRepeatsTheStoredStatusAndSaysItIsAReplay() throws Exception {
        when(placementService.place(USER_ID, "checkout-1", REQUEST)).thenReturn(new PlacementResult.Replayed(201, ORDER));

        placeOrder("checkout-1")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/me/orders/17"))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(17));
    }

    // The header is optional at the binding level; the service turns a missing key into a problem
    // with an error code, so the controller must pass null through rather than answer itself.
    @Test
    void aMissingKeyReachesTheServiceAndComesBackAsAProblemWithAnErrorCode() throws Exception {
        when(placementService.place(eq(USER_ID), isNull(), any()))
                .thenThrow(new InvalidRequestException("IDEMPOTENCY_KEY_MISSING", "The Idempotency-Key header is required"));

        mockMvc.perform(post("/api/v1/me/orders").with(csrf()).with(customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":\"1 Main Street, Pune\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_MISSING"));
    }

    @Test
    void aBlankShippingAddressIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/me/orders").with(csrf()).with(customer())
                        .header("Idempotency-Key", "checkout-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shippingAddress"));

        verifyNoInteractions(placementService);
    }

    @Test
    void listsTheCallersOrdersNewestFirstByDefault() throws Exception {
        when(orderService.listForUser(eq(USER_ID), any())).thenReturn(new PageResponse<>(List.of(ORDER), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/me/orders").with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-2026-000017"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).listForUser(eq(USER_ID), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    @Test
    void sortingByAPropertyOutsideTheWhitelistIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/me/orders").with(customer()).param("sort", "user.password"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    private ResultActions placeOrder(String key) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders").with(csrf()).with(customer())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddress\":\"1 Main Street, Pune\"}"));
    }

    private static RequestPostProcessor customer() {
        return user(new UserPrincipal(USER_ID, "alice@example.com", "hash",
                Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }
}
