-- PostgreSQL Initialization Script for Pipeline Testing
-- Creates tables and sample data for integration tests

-- Users table for simple ETL testing
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    age INTEGER,
    country VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE
);

-- Orders table for join and aggregation testing
CREATE TABLE IF NOT EXISTS orders (
    order_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'pending',
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Products table for multi-source testing
CREATE TABLE IF NOT EXISTS products (
    product_id SERIAL PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(100),
    price DECIMAL(10, 2) NOT NULL,
    stock_quantity INTEGER DEFAULT 0
);

-- Events table for streaming and incremental load testing
CREATE TABLE IF NOT EXISTS events (
    event_id SERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    user_id INTEGER,
    event_data JSONB,
    event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample users
INSERT INTO users (username, email, age, country, is_active) VALUES
    ('alice', 'alice@example.com', 28, 'USA', TRUE),
    ('bob', 'bob@example.com', 35, 'Canada', TRUE),
    ('charlie', 'charlie@example.com', 42, 'UK', TRUE),
    ('diana', 'diana@example.com', 31, 'Australia', FALSE),
    ('eve', 'eve@example.com', 25, 'Germany', TRUE),
    ('frank', 'frank@example.com', 45, 'France', TRUE),
    ('grace', 'grace@example.com', 29, 'USA', TRUE),
    ('henry', 'henry@example.com', 38, 'Canada', FALSE),
    ('iris', 'iris@example.com', 33, 'UK', TRUE),
    ('jack', 'jack@example.com', 27, 'Australia', TRUE)
ON CONFLICT (username) DO NOTHING;

-- Insert sample orders
INSERT INTO orders (user_id, product_name, amount, status) VALUES
    (1, 'Laptop', 1299.99, 'completed'),
    (1, 'Mouse', 29.99, 'completed'),
    (2, 'Keyboard', 89.99, 'shipped'),
    (3, 'Monitor', 399.99, 'completed'),
    (3, 'Webcam', 79.99, 'pending'),
    (4, 'Headphones', 149.99, 'completed'),
    (5, 'Desk Lamp', 45.50, 'completed'),
    (6, 'Office Chair', 299.99, 'shipped'),
    (7, 'USB Cable', 12.99, 'completed'),
    (8, 'Power Bank', 35.00, 'pending'),
    (9, 'Phone Case', 18.50, 'completed'),
    (10, 'Screen Protector', 9.99, 'completed')
ON CONFLICT DO NOTHING;

-- Insert sample products
INSERT INTO products (product_name, category, price, stock_quantity) VALUES
    ('Laptop', 'Electronics', 1299.99, 50),
    ('Mouse', 'Electronics', 29.99, 200),
    ('Keyboard', 'Electronics', 89.99, 150),
    ('Monitor', 'Electronics', 399.99, 75),
    ('Webcam', 'Electronics', 79.99, 100),
    ('Headphones', 'Audio', 149.99, 120),
    ('Desk Lamp', 'Office', 45.50, 80),
    ('Office Chair', 'Furniture', 299.99, 40),
    ('USB Cable', 'Accessories', 12.99, 500),
    ('Power Bank', 'Electronics', 35.00, 180),
    ('Phone Case', 'Accessories', 18.50, 300),
    ('Screen Protector', 'Accessories', 9.99, 400)
ON CONFLICT (product_name) DO NOTHING;

-- Insert sample events
INSERT INTO events (event_type, user_id, event_data) VALUES
    ('login', 1, '{"ip": "192.168.1.1", "device": "desktop"}'),
    ('purchase', 1, '{"product": "Laptop", "amount": 1299.99}'),
    ('logout', 1, '{"session_duration": 1800}'),
    ('login', 2, '{"ip": "192.168.1.2", "device": "mobile"}'),
    ('view_product', 2, '{"product": "Keyboard"}'),
    ('purchase', 2, '{"product": "Keyboard", "amount": 89.99}'),
    ('login', 3, '{"ip": "10.0.0.5", "device": "tablet"}'),
    ('view_product', 3, '{"product": "Monitor"}'),
    ('purchase', 3, '{"product": "Monitor", "amount": 399.99}'),
    ('logout', 3, '{"session_duration": 900}')
ON CONFLICT DO NOTHING;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_users_country ON users(country);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_order_date ON orders(order_date);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_events_event_type ON events(event_type);
CREATE INDEX IF NOT EXISTS idx_events_user_id ON events(user_id);
CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events(event_timestamp);

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO pipelineuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO pipelineuser;

-- Display summary
DO $$
DECLARE
    user_count INTEGER;
    order_count INTEGER;
    product_count INTEGER;
    event_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO user_count FROM users;
    SELECT COUNT(*) INTO order_count FROM orders;
    SELECT COUNT(*) INTO product_count FROM products;
    SELECT COUNT(*) INTO event_count FROM events;

    RAISE NOTICE 'PostgreSQL initialization complete:';
    RAISE NOTICE '  - Users: %', user_count;
    RAISE NOTICE '  - Orders: %', order_count;
    RAISE NOTICE '  - Products: %', product_count;
    RAISE NOTICE '  - Events: %', event_count;
END $$;
