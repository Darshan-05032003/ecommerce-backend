package com.darshan.ecommerce.product;

import com.darshan.ecommerce.IntegrationTest;
import com.darshan.ecommerce.user.Role;
import com.darshan.ecommerce.user.RoleRepository;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
public class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName(Role.ADMIN).orElseThrow();
        userRepository.save(new User("admin@example.com", passwordEncoder.encode("admin123"),
                "Admin User", Set.of(adminRole)));

        String loginJson = "{\"email\":\"admin@example.com\",\"password\":\"admin123\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        adminToken = objectMapper.readTree(response).get("accessToken").asString();
    }

    @Test
    void testCreateAndGetProduct() throws Exception {
        CreateProductRequestDTO req = new CreateProductRequestDTO();
        req.setSku("IT-PROD-001");
        req.setName("Integration Test Product");
        req.setDescription("Desc");
        req.setPrice(new BigDecimal("99.99"));
        req.setStockQuantity(50);

        // Create product (requires admin token)
        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String response = createResult.getResponse().getContentAsString();
        Long productId = objectMapper.readTree(response).get("id").asLong();

        // Get product (public endpoint)
        mockMvc.perform(get("/api/v1/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Integration Test Product"))
                .andExpect(jsonPath("$.price").value(99.99));
    }
}
