package com.darshan.ecommerce.product;

import com.darshan.ecommerce.common.config.ClockConfig;
import com.darshan.ecommerce.common.exception.ProblemDetailFactory;
import com.darshan.ecommerce.common.web.PageResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@Import({ClockConfig.class, ProblemDetailFactory.class})
@WithMockUser
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void defaultsToTheFirstPageOfTwentySortedByNameThenId() throws Exception {
        when(productService.listActive(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));

        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by("name").and(Sort.by("id")));
    }

    @Test
    void passesFiltersAndAWhitelistedSortToTheService() throws Exception {
        when(productService.listActive(any(), any())).thenReturn(new PageResponse<>(List.of(), 2, 100, 0, 0));

        mockMvc.perform(get("/api/v1/products")
                        .param("page", "2").param("size", "100").param("sort", "price,desc")
                        .param("categoryId", "7").param("minPrice", "10.00").param("maxPrice", "20.00")
                        .param("q", "java").param("inStock", "true"))
                .andExpect(status().isOk());

        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "price").and(Sort.by("id")));
        verify(productService).listActive(eq(new ProductFilter(7L, new BigDecimal("10.00"), new BigDecimal("20.00"),
                "java", true)), any());
    }

    @Test
    void pageSizeAboveOneHundredIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));
        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void pageSizeBelowOneIsRejected(String size) throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void negativePageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stockQuantity", "category.name", "price,sideways", "price,asc,name"})
    void sortOutsideTheWhitelistIsRejected(String sort) throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(productService);
    }

    @Test
    void unknownSortPropertyNamesTheAllowedOnes() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "stockQuantity,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Unsupported sort property 'stockQuantity'. Allowed: name, price, createdAt"));
    }

    @Test
    void minPriceAboveMaxPriceIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("minPrice", "50").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("minPrice must not be greater than maxPrice"));
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).listActive(any(), captor.capture());
        return captor.getValue();
    }
}
