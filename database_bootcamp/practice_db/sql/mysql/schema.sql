-- MySQL 8.4 스키마 (labdb)
-- 부트캠프 전반에서 사용

CREATE TABLE IF NOT EXISTS customers (
    id          INT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    country     CHAR(2)      NOT NULL DEFAULT 'KR',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY ux_customers_email (email),
    KEY idx_customers_country (country)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS products (
    id          INT           NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200)  NOT NULL,
    category    VARCHAR(50)   NOT NULL,
    price       DECIMAL(12,2) NOT NULL,
    stock       INT           NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_products_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS orders (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id   INT           NOT NULL,
    total_amount  DECIMAL(12,2) NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_orders_customer (customer_id),
    KEY idx_orders_created (created_at),
    KEY idx_orders_status (status),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_items (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    product_id  INT           NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_oi_order (order_id),
    KEY idx_oi_product (product_id),
    CONSTRAINT fk_oi_order   FOREIGN KEY (order_id)   REFERENCES orders(id),
    CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS accounts (
    id        INT           NOT NULL AUTO_INCREMENT,
    owner     VARCHAR(100)  NOT NULL,
    balance   DECIMAL(15,2) NOT NULL DEFAULT 0,
    version   BIGINT        NOT NULL DEFAULT 0,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
