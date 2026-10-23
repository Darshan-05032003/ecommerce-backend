CREATE TABLE idempotency_keys (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    idem_key        VARCHAR(100) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    response_status SMALLINT     NULL,
    response_body   TEXT         NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_keys_user_key UNIQUE (user_id, idem_key),
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_idempotency_keys_created ON idempotency_keys (created_at);
