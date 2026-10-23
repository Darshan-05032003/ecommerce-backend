package com.darshan.ecommerce.payment;

import com.darshan.ecommerce.common.config.ClockConfig;
import com.darshan.ecommerce.common.exception.ProblemDetailFactory;
import com.darshan.ecommerce.common.exception.UpstreamServiceException;
import com.darshan.ecommerce.common.security.UserPrincipal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Set;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({ClockConfig.class, ProblemDetailFactory.class})
class PaymentControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void aPaymentIs201AndChargesTheCallersOrder() throws Exception {
        PaymentRequest request = new PaymentRequest(PaymentMethod.CARD, "tok_visa", null);
        when(paymentService.pay(USER_ID, 17L, request)).thenReturn(new PaymentResponse(900L, 17L, PaymentMethod.CARD,
                new BigDecimal("40.00"), PaymentStatus.SUCCEEDED, "SIM-CARD-900", null));

        pay("{\"method\":\"CARD\",\"paymentToken\":\"tok_visa\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount").value(40.00));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"method\":\"CHEQUE\"}", "{\"method\":\"CARD\""})
    void anInvalidBodyIsRejectedBeforeAnyAttemptIsRecorded(String body) throws Exception {
        pay(body).andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void aProviderTimeoutIs504WithItsErrorCode() throws Exception {
        PaymentRequest request = new PaymentRequest(PaymentMethod.UPI, null, "timeout@okbank");
        when(paymentService.pay(USER_ID, 17L, request)).thenThrow(UpstreamServiceException.gatewayTimeout(
                "PAYMENT_TIMEOUT", "The payment provider did not answer in time; the order was not paid"));

        pay("{\"method\":\"UPI\",\"upiId\":\"timeout@okbank\"}")
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_TIMEOUT"));
    }

    private ResultActions pay(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders/17/payments").with(csrf()).with(customer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static RequestPostProcessor customer() {
        return user(new UserPrincipal(USER_ID, "alice@example.com", "hash",
                Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }
}
