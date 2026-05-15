-- DB2 11.5 스키마 (LABDB)
-- db2 -tvf schema.sql 로 실행

CREATE TABLE customers (
    id          INTEGER      NOT NULL GENERATED ALWAYS AS IDENTITY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    country     CHAR(2)      NOT NULL DEFAULT 'KR',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_customers_email ON customers(email);
CREATE INDEX idx_customers_country ON customers(country);

CREATE TABLE products (
    id          INTEGER       NOT NULL GENERATED ALWAYS AS IDENTITY,
    name        VARCHAR(200)  NOT NULL,
    category    VARCHAR(50)   NOT NULL,
    price       DECIMAL(12,2) NOT NULL,
    stock       INTEGER       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_products_category ON products(category);

CREATE TABLE orders (
    id            BIGINT        NOT NULL GENERATED ALWAYS AS IDENTITY,
    customer_id   INTEGER       NOT NULL,
    total_amount  DECIMAL(12,2) NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_created  ON orders(created_at);
CREATE INDEX idx_orders_status   ON orders(status);

CREATE TABLE order_items (
    id          BIGINT        NOT NULL GENERATED ALWAYS AS IDENTITY,
    order_id    BIGINT        NOT NULL,
    product_id  INTEGER       NOT NULL,
    quantity    INTEGER       NOT NULL,
    unit_price  DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_oi_order   FOREIGN KEY (order_id)   REFERENCES orders(id),
    CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_oi_order   ON order_items(order_id);
CREATE INDEX idx_oi_product ON order_items(product_id);

CREATE TABLE accounts (
    id         INTEGER       NOT NULL GENERATED ALWAYS AS IDENTITY,
    owner      VARCHAR(100)  NOT NULL,
    balance    DECIMAL(15,2) NOT NULL DEFAULT 0,
    version    BIGINT        NOT NULL DEFAULT 0,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT TIMESTAMP,
    PRIMARY KEY (id)
);
