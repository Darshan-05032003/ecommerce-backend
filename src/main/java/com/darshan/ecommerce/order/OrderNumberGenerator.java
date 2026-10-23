package com.darshan.ecommerce.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;

@Component
public class OrderNumberGenerator {

    // LAST_INSERT_ID(expr) stores expr for the current connection, so the upsert and the
    // following SELECT together act as an atomic "increment and read" on the per-year row.
    private static final String NEXT_VALUE_SQL = """
            INSERT INTO order_number_sequence (order_year, next_value)
            VALUES (?, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE next_value = LAST_INSERT_ID(next_value + 1)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public OrderNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    // A new transaction so the row lock on the counter is released immediately instead of being
    // held until the order transaction commits. Numbers taken by a rolled-back order are skipped,
    // which is the same behaviour as a database sequence.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        int year = Year.now(clock).getValue();
        jdbcTemplate.update(NEXT_VALUE_SQL, year);
        Long value = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return "ORD-%d-%06d".formatted(year, value);
    }
}
