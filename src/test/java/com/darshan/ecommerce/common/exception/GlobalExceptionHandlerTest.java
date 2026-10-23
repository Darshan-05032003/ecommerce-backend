package com.darshan.ecommerce.common.exception;

import com.darshan.ecommerce.common.config.ClockConfig;
import com.darshan.ecommerce.common.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The application's SecurityConfig is not part of the slice, so Boot's default chain
 * stays active: requests run as an authenticated mock user and POSTs carry a CSRF token.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorProbeController.class)
@Import({GlobalExceptionHandlerTest.ErrorProbeController.class, ClockConfig.class, ProblemDetailFactory.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrorsListEveryRejectedField() throws Exception {
        mockMvc.perform(post("/probe/validate").with(csrf())
                        .header(CorrelationIdFilter.HEADER, "corr-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-validation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.instance").value("/probe/validate"))
                .andExpect(jsonPath("$.correlationId").value("corr-validation"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[1].field").value("quantity"))
                .andExpect(jsonPath("$.errors[1].message").value("must be greater than or equal to 1"));
    }

    @Test
    void malformedJsonIsABadRequestProblem() throws Exception {
        mockMvc.perform(post("/probe/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void resourceNotFoundIs404WithTheMessageAsDetail() throws Exception {
        mockMvc.perform(get("/probe/not-found").header(CorrelationIdFilter.HEADER, "corr-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Product not found with id 42"))
                .andExpect(jsonPath("$.instance").value("/probe/not-found"))
                .andExpect(jsonPath("$.correlationId").value("corr-404"));
    }

    @Test
    void conflictIs409() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Email already registered"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void businessRuleViolationIs422() throws Exception {
        mockMvc.perform(get("/probe/business-rule"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Cart is empty"))
                .andExpect(jsonPath("$.errorCode").value("CART_EMPTY"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void aLockFailureIs409RatherThanAServerError() throws Exception {
        mockMvc.perform(get("/probe/deadlock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The request collided with a concurrent request, retry it"));
    }

    @Test
    void accessDeniedIs403() throws Exception {
        mockMvc.perform(get("/probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void unexpectedErrorIs500WithoutInternalDetails() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/unexpected").header(CorrelationIdFilter.HEADER, "corr-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.correlationId").value("corr-500"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("hunter2")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at com.darshan")
                .doesNotContain("trace");
    }

    @Test
    void unknownPathIs404Problem() throws Exception {
        mockMvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance").value("/no/such/path"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void wrongHttpMethodIs405Problem() throws Exception {
        mockMvc.perform(get("/probe/validate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void generatedCorrelationIdIsTheSameInHeaderAndBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andReturn();

        String headerId = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(headerId).matches(UUID_PATTERN);
        assertThat(result.getResponse().getContentAsString()).contains("\"correlationId\":\"" + headerId + "\"");
    }

    @RestController
    @RequestMapping("/probe")
    static class ErrorProbeController {

        record SampleRequest(@NotBlank String name, @Min(1) int quantity) {
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody SampleRequest request) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Product not found with id 42");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Email already registered");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleViolationException("CART_EMPTY", "Cart is empty");
        }

        @GetMapping("/deadlock")
        void deadlock() {
            throw new CannotAcquireLockException("Deadlock found when trying to get lock");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("not yours");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("database password is hunter2");
        }
    }
}
