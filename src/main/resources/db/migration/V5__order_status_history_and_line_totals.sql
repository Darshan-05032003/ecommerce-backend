ALTER TABLE orders ADD COLUMN shipping_address VARCHAR(255) NULL AFTER total_amount;

UPDATE orders SET shipping_address = 'Not recorded' WHERE shipping_address IS NULL;

ALTER TABLE orders
    MODIFY shipping_address VARCHAR(255) NOT NULL,
    ADD CONSTRAINT chk_orders_status CHECK (status IN ('CREATED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

CREATE INDEX idx_orders_status_created ON orders (status, created_at);

ALTER TABLE order_items ADD COLUMN line_total DECIMAL(10, 2) NULL AFTER price;

UPDATE order_items SET line_total = ROUND(price * quantity, 2) WHERE line_total IS NULL;

ALTER TABLE order_items
    MODIFY line_total DECIMAL(10, 2) NOT NULL,
    ADD CONSTRAINT chk_order_items_line_total CHECK (line_total >= 0);

CREATE TABLE order_status_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    changed_by  VARCHAR(120) NOT NULL,
    changed_at  DATETIME(6)  NOT NULL,
    reason      VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB;

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id, changed_at);

INSERT INTO order_status_history (order_id, from_status, to_status, changed_by, changed_at, reason)
SELECT id, NULL, status, created_by, created_at, 'Recorded when status history was introduced'
FROM orders;
