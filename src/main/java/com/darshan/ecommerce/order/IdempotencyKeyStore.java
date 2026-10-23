package com.darshan.ecommerce.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

// Plain JDBC rather than an entity: the claim must be one INSERT issued at an exact point in the
// transaction, and a duplicate must surface as a DuplicateKeyException from that statement.
// Timestamps are written as UTC LocalDateTime so the driver does not shift them by the JVM zone.
@Repository
public class IdempotencyKeyStore {

    public record StoredResponse(String requestHash, Integer status, String body) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public IdempotencyKeyStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public void claim(Long userId, String key, String requestHash) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_keys (user_id, idem_key, request_hash, created_at)
                VALUES (?, ?, ?, ?)""", userId, key, requestHash, utc(clock.instant()));
    }

    public void complete(Long userId, String key, int status, String body) {
        jdbcTemplate.update("""
                UPDATE idempotency_keys SET response_status = ?, response_body = ?
                WHERE user_id = ? AND idem_key = ?""", status, body, userId, key);
    }

    public Optional<StoredResponse> find(Long userId, String key) {
        return jdbcTemplate.query("""
                        SELECT request_hash, response_status, response_body FROM idempotency_keys
                        WHERE user_id = ? AND idem_key = ?""",
                (rs, rowNum) -> new StoredResponse(rs.getString(1), rs.getObject(2, Integer.class), rs.getString(3)),
                userId, key).stream().findFirst();
    }

    public int deleteCreatedBefore(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM idempotency_keys WHERE created_at < ?", utc(cutoff));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
