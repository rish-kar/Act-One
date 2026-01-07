# Act One — Ticketing Backend

Spring Boot (Maven) backend for Prarambh Theatre Group to:
- record ticket purchase/transaction intents
- manually approve/issue tickets
- check-in tickets at the venue (QR code / barcode)
- manage auditoriums (seat inventory)
- accept donations
- collect feedback
- optionally email tickets and generate ticket-card PNGs

---

## Tech stack

- Java 21
- Spring Boot
- Spring Data JPA (Hibernate)
- **Dev/test DB:** H2 in-memory
- **Prod DB:** CockroachDB Cloud (PostgreSQL-compatible)
- Flyway (schema migrations)

---

## Profiles & database

### `dev` (default)
- H2 in-memory
- H2 console enabled at `/h2-console`
- Demo schema + seed data loaded from `schema-h2.sql` / `data-h2.sql`

### `prod`
- CockroachDB Cloud via PostgreSQL JDBC driver
- Flyway migrations run on startup
- `ddl-auto=validate` (schema must already exist and match entities)
- **No seed/init SQL is executed in production**

---

## Environment variables

### Required (production)

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Example CockroachDB JDBC URL pattern (do not commit real creds):

```text
jdbc:postgresql://<host>:26257/<db>?sslmode=verify-full&sslrootcert=<path-to-ca.crt>
```

### Optional (app features)

#### Admin protection
Admin endpoints use a shared secret header.

- `ACTONE_ADMIN_PASSWORD`
  - If blank/empty, most admin endpoints will refuse requests.
  - Send it using HTTP header: `X-Admin-Password: <value>`.

#### Email
- `ACTONE_EMAIL_ENABLED` (default: `true`)
- `ACTONE_EMAIL_FROM` (default: falls back to `ACTONE_SMTP_USERNAME`)
- `ACTONE_EMAIL_BCC` (default: empty)

SMTP settings:
- `ACTONE_SMTP_HOST` (default: `smtp.gmail.com`)
- `ACTONE_SMTP_PORT` (default: `587`)
- `ACTONE_SMTP_USERNAME`
- `ACTONE_SMTP_PASSWORD`
- `ACTONE_SMTP_AUTH` (default: `true`)
- `ACTONE_SMTP_STARTTLS` (default: `true`)
- `ACTONE_SMTP_STARTTLS_REQUIRED` (default: `true`)
- `ACTONE_SMTP_SSL_TRUST` (default: uses `ACTONE_SMTP_HOST`)

#### Ticket card generation
- `ACTONE_TICKET_CARD_ENABLED` (default: `true`)
- `ACTONE_TICKET_CARD_TEMPLATE` (default: `classpath:/static/Card.jpg`)
  - The service will prefer `classpath:/static/Card.jpg` if present (recommended for production).
- `ACTONE_TICKET_CARD_MAX_PARALLEL` (default: `3`)
  - Bounded parallelism used for card generation (email attachments + ZIP downloads)

---

## Run locally

```bash
mvn spring-boot:run
```

Default base URL: `http://localhost:8080`

### H2 console (dev only)
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:tickets`
- Username: `sa`
- Password: *(empty)*

---

## Production deployment notes

1. Apply migrations (Flyway runs automatically on startup).
   - Migrations live in: `src/main/resources/db/migration/`
2. Run with:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

---

## Functionality overview

### Tickets
- Record a transaction intent / ticket request
- Admin approves (issues) tickets
- Venue staff checks-in tickets (one-time) using QR/barcode
- Admin/search endpoints for listing and managing tickets

### Manual transaction flow
- Record a transaction (UPI/etc)
- Admin validates and issues tickets
- Admin can issue/check-in by `userId` or `transactionId`

### Auditoriums
- Admin can create/update auditorium inventory per show
- Admin can query available seats

### Donations
- Public donation creation
- Admin-only donation replacement/patch/delete

### Feedback
- Public feedback submission
- Admin can list/update/delete feedback

### Ticket card downloads
- Generates card PNGs for a user and returns a ZIP

---

## API

### Auth model (admin endpoints)
Admin endpoints require header:

- `X-Admin-Password: <ACTONE_ADMIN_PASSWORD>`

If `ACTONE_ADMIN_PASSWORD` is not set, these endpoints will return 403.

---

### Tickets (`/api/tickets`)

#### Issue / record a ticket transaction
`POST /api/tickets/issue`

Body:
- `showName` (optional if a default show is configured)
- `fullName` (required)
- `email` (required)
- `phoneNumber` (required)
- `ticketCount` (optional, default 1)
- `transactionId` (required)
- `ticketAmount` (required, string decimal)
- `auditoriumId` (optional)
- `showId` (optional)

Returns:
- primary `ticketId`, `qrCodeId`, `userId`, `ticketIds[]`, `qrCodeIds[]`

#### Check-in by QR code id
`POST /api/tickets/qrcode/{qrCodeId}/checkin`

Returns one of:
- `VALID`
- `ALREADY_USED`
- `NOT_FOUND`
- `PENDING_APPROVAL`

#### List distinct shows
`GET /api/tickets/shows`

#### Fetch tickets
- `GET /api/tickets/all`
- `GET /api/tickets/{ticketId}`
- `GET /api/tickets/by-ticket-id/{ticketId}`
- `GET /api/tickets/by-user?userId=...`
- `GET /api/tickets/by-name?fullName=...`
- `GET /api/tickets/by-phone?phoneNumber=...`
- `GET /api/tickets/by-email?email=...`

#### Admin bulk modify by userId
Header required: `X-Admin-Password`

- `PUT /api/tickets/by-user/{userId}` (replace fields)
- `PATCH /api/tickets/by-user/{userId}` (patch fields)
- `DELETE /api/tickets/by-user/{userId}`

#### Delete a single ticket
`DELETE /api/tickets/{ticketId}`

#### Admin: Update status by name + ticketId suffix
Header required: `X-Admin-Password`

`PATCH /api/tickets/status/by-name-suffix`

Body example:
```json
{ "partialName": "Aisha", "ticketIdSuffix": "7df65", "status": "ISSUED" }
```

---

### Check-in helpers (`/api/checkin`)

#### Bulk check-in by phone
`POST /api/checkin/phone/{phoneLast10}`

- `phoneLast10` must be exactly 10 digits

#### Check-in by ticketId suffix
`POST /api/checkin/ticket-suffix/{suffix}`

- `suffix` must be exactly 5 chars

---

### Manual transactions (`/api/transactions`)

#### Record transaction
- `POST /api/transactions/record`
- `POST /api/transactions/record-pending`

Body (both):
- `fullName`, `phoneNumber`, `transactionId`, `ticketAmount`
- `ticketCount` (optional)
- optional `showId`, `showName`, `email`

#### Validate + issue tickets
- `POST /api/transactions/{userId}/validate`
- `POST /api/transactions/{userId}/issue`

#### Check-in tickets
- `POST /api/transactions/{userId}/checkin`

#### Lookup
- `GET /api/transactions/by-phone?phoneNumber=...`
- `GET /api/transactions/by-name?fullName=...`

#### Admin by transactionId
Header required: `X-Admin-Password`

- `POST /api/transactions/by-transaction/{transactionId}/validate`
- `POST /api/transactions/by-transaction/{transactionId}/checkin`
- `GET /api/transactions/by-transaction/{transactionId}`
- `GET /api/transactions/{userId}/transactions`
- `GET /api/transactions/successful`

---

### Auditoriums (`/api/auditoriums`)

- `GET /api/auditoriums`
- `GET /api/auditoriums/{auditoriumId}`

Admin header required:
- `GET /api/auditoriums/{auditoriumId}/available-seats`
- `POST /api/auditoriums`
- `PUT /api/auditoriums/{auditoriumId}`
- `PATCH /api/auditoriums/{auditoriumId}`
- `DELETE /api/auditoriums/{auditoriumId}`

---

### Donations (`/api/donations`)

Public:
- `POST /api/donations`
- `GET /api/donations`
- `GET /api/donations/{serial}`

Admin header required:
- `PUT /api/donations/{id}`
- `PATCH /api/donations/{id}`
- `DELETE /api/donations/{id}`

---

### Feedback (`/api/feedback`)

Public:
- `POST /api/feedback`

Admin header required:
- `GET /api/feedback`
- `GET /api/feedback/{id}`
- `PUT /api/feedback/{id}`
- `PATCH /api/feedback/{id}`
- `DELETE /api/feedback/{id}`
- `PUT /api/feedback/by-user`
- `PATCH /api/feedback/by-user`
- `DELETE /api/feedback/by-user`

---

### Ticket card downloads (`/api/ticket-cards`)

- `GET /api/ticket-cards/by-user/{userId}` → returns `application/zip`
- `GET /api/ticket-cards/by-customer/{customerId}` → alias

---

### Admin (`/api/admin`)

Admin header required:

- `DELETE /api/admin/tickets` (purge all tickets)
- `GET /api/admin/show-name`
- `POST /api/admin/show-name`

Email test:
- `POST /api/admin/email/test`
