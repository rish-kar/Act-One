# Act One — Ticket Issue + Check-in (MVP)

Simple Spring Boot backend for issuing tickets and validating check-ins (no payments).

## Prerequisites

- Java 21+
- Maven 3.9+

## Run locally

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

## H2 console (local only)

When running with the default profile (`application.yaml`), the app uses an in-memory H2 database and preloads a small amount of demo data on startup.

- Console URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:tickets`
- User: `sa`
- Password: *(empty)*

> The production profile disables the H2 console and does not load demo seed data.

## API

### Issue a ticket

**POST** `/api/tickets/issue`

Request body:
- `showName` (required)
- `fullName` (required)
- `email` (required)
- `phoneNumber` (required)

`showId` is auto-generated if not provided.

Example:

```bash
curl -X POST "http://localhost:8080/api/tickets/issue" \
  -H "Content-Type: application/json" \
  -d '{
    "showName": "Act One — Opening Night",
    "fullName": "Alice",
    "email": "alice@example.com",
    "phoneNumber": "+441234567890"
  }'
```

Response:
- `ticketId` (UUID)
- `qrCodeId` (UUID) — random UUID meant to be encoded as a QR code for tickets
- `status` (`ISSUED`)
- `showId` (string, may be null but will be auto-generated when missing)
- `showName`

### Check-in a ticket

**POST** `/api/tickets/{ticketId}/checkin`

Example:

```bash
curl -X POST "http://localhost:8080/api/tickets/{ticketId}/checkin"
```

Response results:
- `{ "result": "VALID" }` (and the ticket is marked as used)
- `{ "result": "ALREADY_USED", "usedAt": "..." }`
- `{ "result": "NOT_FOUND" }`
