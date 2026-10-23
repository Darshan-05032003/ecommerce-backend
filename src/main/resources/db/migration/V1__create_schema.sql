CREATE TABLE roles (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(50)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(120) NOT NULL,
    last_modified_by VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(120) NOT NULL,
    password         VARCHAR(100) NOT NULL,
    full_name        VARCHAR(100) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(120) NOT NULL,
    last_modified_by VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

CREATE TABLE categories (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(120) NOT NULL,
    last_modified_by VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE products (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    sku              VARCHAR(40)    NOT NULL,
    name             VARCHAR(150)   NOT NULL,
    description      VARCHAR(500)   NOT NULL,
    price            DECIMAL(10, 2) NOT NULL,
    stock_quantity   INT            NOT NULL,
    active           BIT(1)         NOT NULL,
    category_id      BIGINT         NULL,
    version          INT            NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       VARCHAR(120)   NOT NULL,
    last_modified_by VARCHAR(120)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_stock CHECK (stock_quantity >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_active ON products (active);

CREATE TABLE cart_items (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    product_id       BIGINT       NOT NULL,
    quantity         INT          NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    created_by       VARCHAR(120) NOT NULL,
    last_modified_by VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_cart_items_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB;

CREATE INDEX idx_cart_items_product ON cart_items (product_id);

CREATE TABLE orders (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    order_number     VARCHAR(20)    NOT NULL,
    user_id          BIGINT         NOT NULL,
    total_amount     DECIMAL(10, 2) NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    version          INT            NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       VARCHAR(120)   NOT NULL,
    last_modified_by VARCHAR(120)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);

CREATE TABLE order_items (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    order_id         BIGINT         NOT NULL,
    product_id       BIGINT         NOT NULL,
    quantity         INT            NOT NULL,
    price            DECIMAL(10, 2) NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       VARCHAR(120)   NOT NULL,
    last_modified_by VARCHAR(120)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (price >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

CREATE TABLE order_number_sequence (
    order_year SMALLINT NOT NULL,
    next_value BIGINT   NOT NULL,
    PRIMARY KEY (order_year)
) ENGINE = InnoDB;
