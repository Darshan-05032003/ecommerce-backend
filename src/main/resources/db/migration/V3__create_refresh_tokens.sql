CREATE TABLE refresh_tokens (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL,
    family_id        VARCHAR(36)  NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    revoked_at       DATETIME(6)  NULL,
    replaced_by      BIGINT       NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(120) NOT NULL,
    last_modified_by VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id)
) ENGINE = InnoDB;

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
