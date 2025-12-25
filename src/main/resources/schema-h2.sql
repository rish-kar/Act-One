-- H2 schema for local development.
-- This exists solely to control physical column order, which Hibernate/JPA does not guarantee.

DROP TABLE IF EXISTS tickets;

CREATE TABLE tickets (
  ticket_id UUID PRIMARY KEY,
  barcode_id VARCHAR(18) NOT NULL,

  show_id VARCHAR(255),
  show_name VARCHAR(255) NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  phone_number VARCHAR(255) NOT NULL,

  customer_id INT,
  transaction_id VARCHAR(255),

  status VARCHAR(32) NOT NULL,

  ticket_count INT NOT NULL,
  ticket_amount DECIMAL(10, 2) NOT NULL,

  created_at_date DATE NOT NULL,
  created_at_time VARCHAR(16) NOT NULL,

  used_at_date DATE,
  used_at_time VARCHAR(16)
);
