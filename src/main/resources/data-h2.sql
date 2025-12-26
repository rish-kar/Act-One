-- Demo seed data for local H2 only.
-- Loaded via spring.sql.init.* in application.yaml.

INSERT INTO tickets (
  ticket_id,
  barcode_id,
  show_id,
  show_name,
  full_name,
  email,
  phone_number,
  customer_id,
  transaction_id,
  status,
  ticket_count,
  ticket_amount,
  created_at_date,
  created_at_time,
  used_at_date,
  used_at_time
)
VALUES
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Aisha Khan', 'test.email@example.com', '7700900101', 1001, 'TXN-DEMO-001', 'ISSUED', 1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Rohan Mehta', 'test.email@example.com', '7700900102', 1002, 'TXN-DEMO-002', 'USED',   1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Emily Carter', 'test.email@example.com', '7700900103', 1003, 'TXN-DEMO-003', 'USED',   1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Daniel Thomas', 'test.email@example.com', '7700900104', 1004, 'TXN-DEMO-004', 'ISSUED', 1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Sanjana Iyer', 'test.email@example.com', '7700900105', 1005, 'TXN-DEMO-005', 'ISSUED', 1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Hannah Patel', 'test.email@example.com', '7700900106', 1006, 'TXN-DEMO-006', 'USED',   1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Kabir Singh', 'test.email@example.com', '7700900107', 1007, 'TXN-DEMO-007', 'ISSUED', 1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Maya Fernandes', 'test.email@example.com', '7700900108', 1008, 'TXN-DEMO-008', 'ISSUED', 1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Oliver Wright', 'test.email@example.com', '7700900109', 1009, 'TXN-DEMO-009', 'USED',   1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-004', 'Act One — Final Night', 'Noor Ahmed', 'test.email@example.com', '7700900110', 1010, 'TXN-DEMO-010', 'ISSUED', 1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-004', 'Act One — Final Night', 'Priya Sharma', 'test.email@example.com', '7700900111', 1011, 'TXN-DEMO-011', 'USED',   1, 750.00, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'));
