INSERT INTO users(username, password, role, email) VALUES
  ('admin', 'admin123', 'ADMIN', 'admin@example.com'),
  ('alice', 'password1', 'USER', 'alice@example.com'),
  ('bob',   'qwerty',    'USER', 'bob@example.com');

INSERT INTO orders(owner_username, product, amount) VALUES
  ('alice', 'Keyboard', 89.99),
  ('alice', 'Mouse',    24.50),
  ('bob',   'Monitor',  299.00),
  ('admin', 'Server',  4999.00);

INSERT INTO posts(author, title, content) VALUES
  ('alice', '안녕하세요', '첫 게시글입니다.'),
  ('bob',   'Tip', 'Spring 사용기...');
