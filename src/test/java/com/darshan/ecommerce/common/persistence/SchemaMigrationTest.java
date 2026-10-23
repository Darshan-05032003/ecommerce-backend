package com.darshan.ecommerce.common.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesEveryMigrationSuccessfully() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4");
    }

    @Test
    void createsAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);

        assertThat(tables).contains("roles", "users", "user_roles", "categories", "products",
                "cart_items", "orders", "order_items", "order_number_sequence", "refresh_tokens");
    }

    @Test
    void createsIndexesForQueryPaths() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = DATABASE()",
                String.class);

        assertThat(indexes).contains(
                "uk_users_email", "uk_products_sku", "uk_cart_items_user_product", "uk_orders_order_number",
                "idx_products_category", "idx_products_active_category", "idx_cart_items_product",
                "idx_orders_user_created", "idx_order_items_order", "idx_order_items_product",
                "uk_refresh_tokens_token_hash", "idx_refresh_tokens_family", "idx_refresh_tokens_user");
        assertThat(indexes).doesNotContain("idx_products_active");
    }

    @Test
    void requiresACategoryOnEveryProduct() {
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'products' AND column_name = 'category_id'", String.class);

        assertThat(nullable).isEqualTo("NO");
    }

    @Test
    void seedsBothRoles() {
        List<String> roles = jdbcTemplate.queryForList("SELECT name FROM roles ORDER BY name", String.class);

        assertThat(roles).containsExactly("ROLE_ADMIN", "ROLE_CUSTOMER");
    }
}
