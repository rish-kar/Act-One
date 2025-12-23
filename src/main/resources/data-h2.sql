-- Demo seed data for local H2 only.
-- Loaded via spring.sql.init.* in application.yaml.

INSERT INTO tickets (ticket_id, barcode_id, show_id, show_name, full_name, email, phone_number, status, created_at, used_at)
VALUES
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-001', 'Act One — Opening Night', 'Aisha Khan', 'aisha.khan@example.com', '+44 7700 900101', 'ISSUED', DATEADD('MINUTE', -120, CURRENT_TIMESTAMP()), NULL),
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-001', 'Act One — Opening Night', 'Rohan Mehta', 'rohan.mehta@example.com', '+44 7700 900102', 'USED',   DATEADD('MINUTE', -180, CURRENT_TIMESTAMP()), DATEADD('MINUTE', -15, CURRENT_TIMESTAMP())),
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-001', 'Act One — Opening Night', 'Emily Carter', 'emily.carter@example.com', '+44 7700 900103', 'USED',   DATEADD('MINUTE', -200, CURRENT_TIMESTAMP()), DATEADD('MINUTE', -12, CURRENT_TIMESTAMP())),

  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Daniel Thomas', 'daniel.thomas@example.com', '+44 7700 900104', 'ISSUED', DATEADD('DAY', -1, CURRENT_TIMESTAMP()), NULL),
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Sanjana Iyer', 'sanjana.iyer@example.com', '+44 7700 900105', 'ISSUED', DATEADD('DAY', -1, DATEADD('MINUTE', -30, CURRENT_TIMESTAMP())), NULL),
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-002', 'Act One — Saturday Matinee', 'Hannah Patel', 'hannah.patel@example.com', '+44 7700 900106', 'USED',   DATEADD('DAY', -1, DATEADD('MINUTE', -80, CURRENT_TIMESTAMP())), DATEADD('DAY', -1, DATEADD('MINUTE', -10, CURRENT_TIMESTAMP()))),

  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-003', 'Act One — Community Preview', 'Kabir Singh', 'kabir.singh@example.com', '+44 7700 900107', 'ISSUED', DATEADD('DAY', -3, CURRENT_TIMESTAMP()), NULL),
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-003', 'Act One — Community Preview', 'Maya Fernandes', 'maya.fernandes@example.com', '+44 7700 900108', 'ISSUED', DATEADD('DAY', -3, DATEADD('MINUTE', -45, CURRENT_TIMESTAMP())), NULL),
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-003', 'Act One — Community Preview', 'Oliver Wright', 'oliver.wright@example.com', '+44 7700 900109', 'USED',   DATEADD('DAY', -3, DATEADD('MINUTE', -90, CURRENT_TIMESTAMP())), DATEADD('DAY', -3, DATEADD('MINUTE', -5, CURRENT_TIMESTAMP()))),

  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-004', 'Act One — Final Night', 'Noor Ahmed', 'noor.ahmed@example.com', '+44 7700 900110', 'ISSUED', DATEADD('DAY', -7, CURRENT_TIMESTAMP()), NULL),
  (RANDOM_UUID(), RANDOM_UUID(), 'SHOW-2025-004', 'Act One — Final Night', 'Priya Sharma', 'priya.sharma@example.com', '+44 7700 900111', 'USED',   DATEADD('DAY', -7, DATEADD('MINUTE', -120, CURRENT_TIMESTAMP())), DATEADD('DAY', -7, DATEADD('MINUTE', -20, CURRENT_TIMESTAMP())));
