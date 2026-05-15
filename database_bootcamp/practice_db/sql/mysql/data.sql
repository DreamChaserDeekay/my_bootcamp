-- 샘플 데이터 (MySQL)

INSERT INTO customers (name, email, country) VALUES
    ('Alice',   'alice@example.com',   'KR'),
    ('Bob',     'bob@example.com',     'US'),
    ('Carol',   'carol@example.com',   'KR'),
    ('David',   'david@example.com',   'JP'),
    ('Eve',     'eve@example.com',     'KR'),
    ('Frank',   'frank@example.com',   'GB'),
    ('Grace',   'grace@example.com',   'KR'),
    ('Henry',   'henry@example.com',   'US'),
    ('Iris',    'iris@example.com',    'KR'),
    ('Jack',    'jack@example.com',    'JP');

INSERT INTO products (name, category, price, stock) VALUES
    ('MacBook Pro 14',      'Laptop',     2390000, 50),
    ('Dell XPS 15',         'Laptop',     1890000, 30),
    ('iPhone 15 Pro',       'Phone',      1550000, 200),
    ('Galaxy S24',          'Phone',      1390000, 150),
    ('iPad Air',            'Tablet',      890000, 80),
    ('Apple Watch Ultra',   'Wearable',    990000, 100),
    ('AirPods Pro',         'Audio',       360000, 500),
    ('Sony WH-1000XM5',     'Audio',       450000, 300),
    ('Logitech MX Master',  'Accessory',   120000, 1000),
    ('USB-C Hub',           'Accessory',    50000, 2000);

INSERT INTO orders (customer_id, total_amount, status, created_at) VALUES
    (1, 2390000, 'PAID',      '2026-05-01 10:00:00'),
    (1,  360000, 'PAID',      '2026-05-03 14:30:00'),
    (2, 1550000, 'PAID',      '2026-05-02 09:15:00'),
    (3,  890000, 'PENDING',   '2026-05-05 11:00:00'),
    (4, 1390000, 'PAID',      '2026-05-04 16:20:00'),
    (5,  450000, 'CANCELLED', '2026-05-06 13:45:00'),
    (1,  120000, 'PAID',      '2026-05-08 08:30:00'),
    (6, 1890000, 'PAID',      '2026-05-09 17:00:00'),
    (7,   50000, 'PAID',      '2026-05-10 12:00:00'),
    (8,  990000, 'FAILED',    '2026-05-11 19:30:00');

INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES
    (1, 1, 1, 2390000),
    (2, 7, 1,  360000),
    (3, 3, 1, 1550000),
    (4, 5, 1,  890000),
    (5, 4, 1, 1390000),
    (6, 8, 1,  450000),
    (7, 9, 1,  120000),
    (8, 2, 1, 1890000),
    (9,10, 1,   50000),
    (10,6, 1,  990000);

INSERT INTO accounts (owner, balance) VALUES
    ('Alice', 1000000),
    ('Bob',    500000),
    ('Carol',  300000);
