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
  created_at_date,
  created_at_time,
  used_at_date,
  used_at_time
)
VALUES
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Aisha Khan', 'aisha.khan@example.com', '+44 7700 900101', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Rohan Mehta', 'rohan.mehta@example.com', '+44 7700 900102', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-001', 'Act One — Opening Night', 'Emily Carter', 'emily.carter@example.com', '+44 7700 900103', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Daniel Thomas', 'daniel.thomas@example.com', '+44 7700 900104', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Sanjana Iyer', 'sanjana.iyer@example.com', '+44 7700 900105', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Hannah Patel', 'hannah.patel@example.com', '+44 7700 900106', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Kabir Singh', 'kabir.singh@example.com', '+44 7700 900107', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Maya Fernandes', 'maya.fernandes@example.com', '+44 7700 900108', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-003', 'Act One — Community Preview', 'Oliver Wright', 'oliver.wright@example.com', '+44 7700 900109', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a')),

  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-004', 'Act One — Final Night', 'Noor Ahmed', 'noor.ahmed@example.com', '+44 7700 900110', 'ISSUED', CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), NULL, NULL),
  (RANDOM_UUID(), SUBSTRING(RANDOM_UUID()::VARCHAR, 1, 18), 'SHOW-2025-004', 'Act One — Final Night', 'Priya Sharma', 'priya.sharma@example.com', '+44 7700 900111', 'USED',   CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'), CURRENT_DATE(), FORMATDATETIME(CURRENT_TIMESTAMP(), 'hh:mm a'));
