# Act One — Ticketing Backend

Welcome to Act One — the small, pragmatic ticketing backend that powers Prarambh Theatre's shows.
This README tells the story of the product from the viewpoint of the operator: how a ticket request
is recorded, how a volunteer validates and issues tickets, how attendees check-in at the door,
and how the system composes and delivers beautiful ticket cards by email.

The goal here is to be practical: runnable locally for development, auditable for production, and
easy to deploy to Google Cloud Run.

Quick story (the short version)

- Alice (patron) visits the box office and gives her name, phone and email.
- The frontend posts a purchase request to Act One; the backend records ticket row(s) in the DB
  with status `TRANSACTION_MADE`.
- A human (or a payment webhook) validates the transaction — the staff triggers `ISSUED`.
- When a ticket transitions to `ISSUED`, the backend generates an in-memory JPG "ticket card"
  (no disk writes) and sends an aggregated purchase email with attachments.
- At the door, staff scans the QR; the system verifies the ticket's status and marks it `USED`.

If you'd rather skip the story, jump to "Run locally" or "API examples" below.

---

Table of contents

- Overview & architecture
- Quickstart (run locally)
- API examples and curl snippets
- Ticket issuance & email flow (how attachments are generated)
- Admin operations (delete users, purge, resend emails)
- Production deployment (Docker + Cloud Run)
- CI / GitHub Actions (recommended)
- Troubleshooting & logs (HikariCP warning, SMTP TLS)
- Contributing and next steps

---

## Overview & architecture

Act One is intentionally small and opinionated. Key principles:

- Single-process Spring Boot application (Java 21) with Spring Data JPA and H2 for dev.
- Email templates: simple plaintext messages that include ticket details and attach in-memory
  JPG ticket cards generated from a classpath template (`Card.jpg`).
- Ticket card generation is CPU-bound image drawing (QR code + text); generation is done in
  bounded parallel threads to avoid overloading the JVM in container environments.
- Email sending is performed with a pluggable `EmailSender` implementation; SMTP is the default.

Core components (high level)

- `TicketIssuanceService` — creates/persists tickets (status TRANSACTION_MADE) and publishes
  events when purchases are issued.
- `ManualTransactionService` — supports validating/issuing tickets by `userId` or `transactionId`.
- `TicketCardGenerator` — generates JPG bytes of the ticket card using ImageIO + ZXing.
- `TicketPurchaseEmailListener` — listens for issued events, generates JPG attachments in parallel,
  and sends a single purchase-level email with all attachments.
- `SmtpEmailSender` — sends mail; has retry logic for transient TLS/handshake errors.

Why JPG and not PNG? JPG attachments are smaller and more widely supported for email clients; the
system generates JPG bytes in-memory to avoid filesystem writes in serverless containers.

---

## Run locally (developer quickstart)

Prereqs:
- Java 21
- Maven 3.8+
- (optional) Docker for container testing

1) Build and run the app locally (dev profile uses H2 and local resources):

```bash
# Build (optional)
mvn clean package

# Run in dev profile (default)
mvn spring-boot:run
```

Default base URL: `http://localhost:8080`
H2 console (dev only): `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:tickets`, user `sa`)

Tip: The test profile bundles a `Card.jpg` in `src/test/resources/static/Card.jpg` so tests can
exercise card generation.

---

## API examples & handy curl snippets

Authentication model for admin endpoints: a shared-secret header `X-Admin-Password` which must
match `ACTONE_ADMIN_PASSWORD` in the environment (or it will be rejected with `403`). This is
intended for internal/dev use only — not a substitute for real auth.

Create a user (admin-only):

```bash
curl -X POST 'http://localhost:8080/api/users' \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Password: prarambh-admin-delhi' \
  --data '{"fullName":"John Doe","phoneNumber":"9876543210","email":"john@example.com"}'
```

Delete a single user (admin-only):

```bash
curl -X DELETE 'http://localhost:8080/api/users/123' \
  -H 'X-Admin-Password: prarambh-admin-delhi'
```

Delete ALL users (admin-only) — careful with this one:

```bash
curl --location --request DELETE 'http://localhost:8080/api/users/' \
  --header 'X-Admin-Password: prarambh-admin-delhi'
```

Resend ticket-issue email by full name + transactionId (admin-only). This endpoint will:
- find tickets matching transactionId
- filter by `fullName` (case-insensitive)
- check all matching tickets are `ISSUED` and then re-generate attachments and send the email

```bash
curl --location --request POST 'http://localhost:8080/api/resend-email/by-name-and-transaction?fullName=John%20Doe&transactionId=TXN-1234' \
  --header 'X-Admin-Password: prarambh-admin-delhi'
```

Issue tickets (public-facing; returns the created ticket rows):

```bash
curl -X POST 'http://localhost:8080/api/tickets/issue' \
  -H 'Content-Type: application/json' \
  --data '{"fullName":"Alice", "email":"alice@example.com", "phoneNumber":"9999999999", "ticketCount":2, "ticketAmount":"500.00"}'
```

Check-in a ticket by QR code id (door staff):

```bash
curl -X POST 'http://localhost:8080/api/tickets/qrcode/<qrCodeId>/checkin'
```

Lookups: by name, by phone, by email are all admin-protected endpoints under `/api/tickets`.

---

## Ticket issuance & email flow — what's happening under the hood

When a ticket is issued, the application:
1. Persists ticket rows (one per seat) in the `tickets` table with status `TRANSACTION_MADE`.
2. When a manual validation/issue happens, the status is moved to `ISSUED`.
3. The app publishes a `TicketPurchaseIssuedEvent` (list of tickets) which the
   `TicketPurchaseEmailListener` consumes asynchronously AFTER COMMIT.
4. The listener generates ticket-card JPG attachments in parallel (bounded by
   `ACTONE_TICKET_CARD_MAX_PARALLEL`) using `TicketCardGenerator.generateTicketCardJpegBytes(t)`.
5. The attachments are passed to `EmailSender.send(...)` which delivers the email.

Important implementation notes:
- All JPG generation is done in-memory (no temp files) to be container-friendly.
- Card template is `Card.jpg` under `classpath:/static/` (the repo includes one for tests).
- If email is disabled via `ACTONE_EMAIL_ENABLED=false`, the system logs and skips sending.

---

## Admin operations you might need

- Purge ALL tickets (admin): `DELETE /api/admin/tickets` (header required)
- Delete users (admin): `DELETE /api/users/{id}` or `DELETE /api/users/` (purge)
- Resend email by name+transaction (admin): `POST /api/resend-email/by-name-and-transaction` (see example above)

These endpoints are helpful when you need to correct data or re-send emails if a delivery failed.

---

## Production deployment (Docker + Google Cloud Run)

The following is a repeat of the short commands, but with an example `env-prod.yaml` template
shown and additional notes about secrets.

1) Build the artifact

```powershell
mvn clean package
```

2) Build Docker image

```powershell
docker build -t act-one:latest .
```

3) Tag

```powershell
docker tag act-one:latest asia-south1-docker.pkg.dev/<PROJECT_ID>/actone-repo/act-one:latest
```

4) Authenticate & push

```powershell
# login & configure docker helper
gcloud auth login
gcloud config set project <PROJECT_ID>
gcloud auth configure-docker asia-south1-docker.pkg.dev

docker push asia-south1-docker.pkg.dev/<PROJECT_ID>/actone-repo/act-one:latest
```

5) Deploy to Cloud Run

```powershell
# Provide production env vars via a secure file (do not commit secrets)
gcloud run deploy act-one-backend \
  --region asia-south1 \
  --image asia-south1-docker.pkg.dev/<PROJECT_ID>/actone-repo/act-one:latest \
  --env-vars-file env-prod.yaml
```
---

## CI / GitHub Actions (recommended)

A simple workflow you can add to automatically build/test/package and push to Artifact Registry:

- Steps:
  - Checkout
  - Set up JDK 21
  - mvn -B -DskipTests package
  - Build docker image
  - Authenticate to GCP using a service account key stored as `GCP_SA_KEY` secret
  - gcloud config set project
  - gcloud auth configure-docker
  - docker push
  - gcloud run deploy (optional)

If you'd like, I can add a ready-to-use `.github/workflows/ci-deploy.yml` that does the above
with careful secrets handling.

---

## Troubleshooting & logs — real-world problems we've handled

1. HikariPool "Thread starvation or clock leap detected" warning

- Symptom: a log line like
  `HikariPool-1 - Thread starvation or clock leap detected (housekeeper delta=1m9s...)`

- Cause: in serverless/cloud environments the JVM can be paused or experience clock skew
  (e.g. during hibernation or node scheduling). HikariCP's default housekeeping interval is
  short and can interpret these pauses as thread starvation.

- What we changed: in `application-prod.yml` we set `spring.datasource.hikari.housekeeping-period-ms`
  to 300000 (5 minutes) and tuned `idle-timeout` / `max-lifetime`. This reduces false-positive
  warnings without changing connection semantics.

2. SMTP TLS/SSL handshake errors when sending via external SMTP (e.g. Gmail)

- Symptom (example log):
  `javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake` or
  `Could not convert socket to TLS` during `mailSender.send(...)`.

- Cause: transient network issues, remote policy blocking, or TLS version/protocol mismatch.
  In cloud run these can be intermittent.

- What we changed: `SmtpEmailSender` now retries transient TLS/handshake failures with exponential
  backoff (1s, 4s, 9s). This dramatically reduces transient failure surface. If failures persist
  consider:
  - validating SMTP settings (host/port/auth/starttls)
  - using a transactional provider (SendGrid/Mailgun) that offers an HTTP API (more robust in
    serverless environments)
  - verifying TLS cipher/protocol compatibility between the runtime and remote SMTP host

3. Large attachments / slow SMTP sends cause timeouts

- We log attachment sizes and time taken to generate and send. If emails take too long, consider
  lowering `ACTONE_TICKET_CARD_MAX_PARALLEL` or offloading card generation to an external worker
  (e.g. Cloud Tasks or a dedicated microservice).

---

## Developer notes & testability

- Unit & integration tests exercise ticket generation and email flows; see `src/test/java/...`.
- There is a convenience runner `BarcodePngSmokeRunner` (not a unit test) that can locally
  generate a card JPG for manual inspection.
- The ticket-card template and fonts are intentionally simple. For production, provide a
  high-resolution `Card.jpg` in `src/main/resources/static/`.

---

## Next steps & enhancements (ideas)

- Replace the shared-secret admin header with real authentication (OIDC/JWT) for production.
- Add a resilient email-sending queue with persistent retry tracking and alerting.
- Move sensitive config to Secret Manager and enable Workload Identity for Cloud Run.
- Add an HTML email template (Thymeleaf) for nicer customer-facing emails while keeping
  a plaintext fallback.

---

## Contact & help

If you hit a deployment roadblock or want a CI pipeline added, tell me which provider you use
(GitHub Actions, GitLab CI, Jenkins) and I will add a tested workflow.

---

## Appendix: quick reference commands

- Build JAR: `mvn clean package`
- Run (dev): `mvn spring-boot:run`
- Docker build: `docker build -t act-one:latest .`
- Docker tag: `docker tag act-one:latest asia-south1-docker.pkg.dev/<PROJECT>/actone-repo/act-one:latest`
- Docker push: `docker push asia-south1-docker.pkg.dev/<PROJECT>/actone-repo/act-one:latest`
- Deploy: `gcloud run deploy act-one-backend --region asia-south1 --image <IMAGE> --env-vars-file env-prod.yaml`


---

End of README — enjoy shipping shows!
