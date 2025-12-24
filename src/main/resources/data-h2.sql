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
  status,
  ticket_count,
  created_at_date,
  created_at_time,
  used_at_date,
  used_at_time
)
VALUES
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Aisha Khan', 'test.email@example.com', '+44 7700 900101', 'ISSUED', 1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Rohan Mehta', 'test.email@example.com', '+44 7700 900102', 'USED',   1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Emily Carter', 'test.email@example.com', '+44 7700 900103', 'USED',   1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Daniel Thomas', 'test.email@example.com', '+44 7700 900104', 'ISSUED', 1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Sanjana Iyer', 'test.email@example.com', '+44 7700 900105', 'ISSUED', 1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Hannah Patel', 'test.email@example.com', '+44 7700 900106', 'USED',   1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Kabir Singh', 'test.email@example.com', '+44 7700 900107', 'ISSUED', 1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Maya Fernandes', 'test.email@example.com', '+44 7700 900108', 'ISSUED', 1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Oliver Wright', 'test.email@example.com', '+44 7700 900109', 'USED',   1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-004', 'Act One — Final Night', 'Noor Ahmed', 'test.email@example.com', '+44 7700 900110', 'ISSUED', 1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-004', 'Act One — Final Night', 'Priya Sharma', 'test.email@example.com', '+44 7700 900111', 'USED',   1, CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'));
