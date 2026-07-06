# audit-service

Centralized audit logging for the platform - see roadmap Step 4. Every other
service publishes domain events instead of writing audit rows directly; this
is the only service with an `audit_logs` table.

## How it gets its data

It doesn't call anyone. It just binds its own queue
(`arceus.audit.events.queue`) to the shared `arceus.events.exchange` with
routing key `#` - meaning it receives every event any service ever
publishes, present or future, without needing a code change on either side
when a new event type shows up. See `shared/arceus-events` for the envelope
format (`EventEnvelope`) and the currently-defined events
(`UserRegisteredEvent`, `LoginSuccessfulEvent`, `LoginFailedEvent`,
`PasswordChangedEvent`, `EmailChangedEvent`, `PhoneChangedEvent`,
`UserLoggedOutEvent`), all published today by auth-service.

`DomainEventAuditConsumer` turns each envelope into one `AuditLog` row: the
raw event type, a best-effort actor email (pulled from whichever of
`email`/`newEmail`/`oldEmail` the payload has), a short human-readable
description, and the full original payload as JSON for anything the
description doesn't capture.

## API

```
GET /audit-logs?eventType=&actorEmail=&from=&to=&q=&page=&size=
GET /audit-logs/{id}
```

- `eventType` - exact match, e.g. `LOGIN_FAILED`
- `actorEmail` - exact match
- `from` / `to` - ISO-8601 instants, filters on `occurredAt`
- `q` - case-insensitive substring search over `description`
- `page` / `size` - standard pagination, `size` capped at 100

Own Postgres database (`arceus_audit`) - per the platform rule that services
never share a database or read each other's schema.

## Running it

Needs: Postgres and RabbitMQ (same instance/vhost as the other services -
see the root `.env`).

```powershell
.\mvnw.cmd -pl services/audit-service spring-boot:run
```

Swagger UI at `http://localhost:8083/swagger-ui.html`.

## Known limitations worth knowing about

- **No dead-letter queue.** A malformed envelope or a DB hiccup just logs an
  error and drops that one audit row - same caveat as notification-service's
  OtpConsumer.
- **`actorEmail` is best-effort.** Events with no email-shaped field at all
  (none currently, but future ones might) will have `actorEmail = null`.
- **Read-only API.** There's no delete/redact endpoint - if this platform
  ever needs to support "right to be forgotten," that's a real gap to close
  before it matters, not after.
