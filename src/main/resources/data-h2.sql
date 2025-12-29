-- Demo seed data for local H2 only.
-- Loaded via spring.sql.init.* in application.yaml.

INSERT INTO tickets (
  ticket_id,
  show_id,
  show_name,
  full_name,
  email,
  phone_number,
  status,
  created_at_date,
  created_at_time,
  used_at_date,
  used_at_time,
  qr_code_id,
  ticket_count,
  customer_id,
  transaction_id,
  ticket_amount
)
VALUES
  (RANDOM_UUID(), 'SHOW-2025-001', 'Act One — Opening Night', 'Aisha Khan', 'test.email@example.com', '7700900101', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL, SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1001, 'TXN-DEMO-001', 750.00),
  (RANDOM_UUID(), 'SHOW-2025-001', 'Act One — Opening Night', 'Rohan Mehta', 'test.email@example.com', '7700900102', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1002, 'TXN-DEMO-002', 750.00),
  (RANDOM_UUID(), 'SHOW-2025-001', 'Act One — Opening Night', 'Emily Carter', 'test.email@example.com', '7700900103', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1003, 'TXN-DEMO-003', 750.00),

  (RANDOM_UUID(), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Daniel Thomas', 'test.email@example.com', '7700900104', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL, SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1004, 'TXN-DEMO-004', 750.00),
  (RANDOM_UUID(), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Sanjana Iyer', 'test.email@example.com', '7700900105', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL, SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1005, 'TXN-DEMO-005', 750.00),
  (RANDOM_UUID(), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Hannah Patel', 'test.email@example.com', '7700900106', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1006, 'TXN-DEMO-006', 750.00),

  (RANDOM_UUID(), 'SHOW-2025-003', 'Act One — Community Preview', 'Kabir Singh', 'test.email@example.com', '7700900107', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL, SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1007, 'TXN-DEMO-007', 750.00),
  (RANDOM_UUID(), 'SHOW-2025-003', 'Act One — Community Preview', 'Maya Fernandes', 'test.email@example.com', '7700900108', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL, SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1008, 'TXN-DEMO-008', 750.00),
  (RANDOM_UUID(), 'SHOW-2025-003', 'Act One — Community Preview', 'Oliver Wright', 'test.email@example.com', '7700900109', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1009, 'TXN-DEMO-009', 750.00),

  (RANDOM_UUID(), 'SHOW-2025-004', 'Act One — Final Night', 'Noor Ahmed', 'test.email@example.com', '7700900110', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL, SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1010, 'TXN-DEMO-010', 750.00),
  (RANDOM_UUID(), 'SHOW-2025-004', 'Act One — Final Night', 'Priya Sharma', 'test.email@example.com', '7700900111', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 1, 1011, 'TXN-DEMO-011', 750.00);

-- Seed donations (6 sample records) - serial_number is managed by the app but we include for H2 seed
INSERT INTO donations (serial_number, full_name, phone_number, email, message, amount) VALUES
  ('1000000001', 'Arjun Verma', '9916604905', 'arjun.verma@example.com', 'Happy to support', 100.00),
  ('1000000002', 'Meera Rao', '9876543210', 'meera.rao@example.com', 'All the best for the show', 150.00),
  ('1000000003', 'Samir Khan', '9900112233', 'samir.khan@example.com', 'Great initiative', 200.00),
  ('1000000004', 'Lina Das', '9988776655', 'lina.das@example.com', 'Lovely production', 50.00),
  ('1000000005', 'Tom Harris', '1234567890', 'tom.harris@example.com', 'Supporting the arts', 250.00),
  ('1000000006', 'Aisha Noor', '9876543210', 'aisha.noor@example.com', 'Break a leg!', 125.00);
