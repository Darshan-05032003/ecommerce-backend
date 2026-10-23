CREATE TABLE payments (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL,
    method            VARCHAR(20)   NOT NULL,
    amount            DECIMAL(10, 2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    gateway_reference VARCHAR(100)  NULL,
    failure_reason    VARCHAR(255)  NULL,
    refunded_at       DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    created_by        VARCHAR(120)  NOT NULL,
    last_modified_by  VARCHAR(120)  NOT NULL,
    -- Holds the order id only while an attempt is in flight or has succeeded, so the unique index
    -- below allows any number of failed attempts but at most one live payment per order.
    active_order_id   BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('INITIATED', 'SUCCEEDED') THEN order_id END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uk_payments_active_order UNIQUE (active_order_id),
    CONSTRAINT chk_payments_method CHECK (method IN ('CARD', 'UPI', 'COD')),
    CONSTRAINT chk_payments_status CHECK (status IN ('INITIATED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_payments_order ON payments (order_id, created_at);
