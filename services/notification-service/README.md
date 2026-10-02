# notification-service

Owns delivery: this is the only service that actually talks to Gmail's SMTP
server or 2Factor's SMS API. It has no idea what a `User` is, no Postgres
connection, and no concept of JWTs - its entire job is "given a channel,
a recipient, and a code, get it delivered."

auth-service (and later, any other service that needs to notify someone)
publishes a message describing what to send; this service consumes it and
does the actual sending. See `shared/arceus-events` for the message contract
both sides agree on, and the root README for the overall flow.

## What it does NOT do

- Doesn't generate or store OTP codes for email (auth-service does that -
  the code is already decided by the time the message arrives here)
- Doesn't decide whether a code is correct (verification is identity logic,
  stays in auth-service)
- Doesn't know who a "user" is at all - just a recipient address/number

## The two delivery paths, and why they're not symmetric

**Email**: the message arrives with the OTP code already generated. This
service just formats an email and sends it via `EmailService` (Gmail SMTP).

**SMS**: the message arrives with `otpCode = null`. That's not a bug -
2Factor.in generates its own OTP when you call its send endpoint, so there's
nothing to pass in ahead of time. `PhoneOtpSender` calls 2Factor, gets back
a session id, and writes that session id into **shared Redis** under a key
`OtpRedisKeys.phoneSession(phoneNumber)` defines - the exact key auth-service
reads from later when the user submits their code for verification.

This is the one place the two services touch the same data without going
through RabbitMQ - deliberately, since Redis is shared infrastructure both
services have a legitimate direct connection to, not a service-to-service call.

## Running it

Needs: Redis, RabbitMQ, and real credentials for Gmail SMTP + 2Factor (see
`.env.example` - same `.env` file as auth-service, both services read
from the one at the project root).

```powershell
.\mvnw.cmd -pl services/notification-service spring-boot:run
```

No database, no Swagger UI here (it has no HTTP endpoints - it's a pure
message consumer) - the only way to observe it working is:
1. Its own console log (`OtpConsumer` logs an error if delivery fails,
   nothing if it succeeds - see below)
2. RabbitMQ's management UI at `http://localhost:15672` (or your CloudAMQP
   dashboard if you're using their hosted free tier) - watch the queue's
   message count go up when auth-service publishes, and back down when this
   service consumes
3. Actually checking the email inbox / phone the OTP was meant to reach

## Known limitations worth knowing about

- **No dead-letter queue.** If Gmail or 2Factor is down, or a phone number
  is malformed, `OtpConsumer.handle()` catches the exception, logs it, and
  the message is gone - no retry, no way to know later that a specific user
  never got their code except by them saying "I never got the OTP." Adding
  a DLQ + retry policy is the natural next step here.
- **No success logging** - only failures are logged right now. Fine for
  debugging during development; you'd want a success log line (or a metric)
  before trusting this in anything resembling production, so you can tell
  "nothing's failing" apart from "nothing's running."
- **2Factor's phone number format** isn't validated or normalized here -
  it sends whatever string it received. If auth-service ever changes what
  format it stores phone numbers in, this breaks silently until someone
  notices OTPs stopped arriving.

## The generic /notifications API

Everything above (OtpConsumer) is the original, narrow "auth-service sends
OTP emails" path and is untouched. Alongside it, this service now exposes
the platform-wide notification pipeline:

```
POST /notifications
{
    "channel": "EMAIL",
    "template": "WELCOME",
    "to": "user@example.com",
    "variables": { "name": "John" }
}
```

Own Postgres database now (`arceus_notification`, own schema per the
platform rule that services never share a database):

- `notification_templates` - one row per (channel, code). Only the
  `WELCOME` EMAIL template is seeded on startup (`TemplateSeeder`) since
  it's the only one anything currently sends.
- `notification_records` - one row per POST /notifications call. Doubles as
  delivery status (`GET /notifications/{id}`) and history
  (`GET /notifications?channel=&status=&template=&to=&page=&size=`).

Only `EMAIL` actually sends anywhere (via `EmailService.sendRenderedEmail`,
same Gmail SMTP connection as OtpConsumer). `SMS`/`PUSH`/`SLACK`/`DISCORD`/
`WEBHOOK` exist as channel values so the API shape won't change later, but
`NotificationDispatchService` throws `UnsupportedOperationException` for
them today - that's caught, recorded as `FAILED`, and left for someone to
implement without touching the contract other services already depend on.

Retries are polling-based (`NotificationRetryScheduler`, every
`notification.retry-backoff-seconds`, default 30s) - no new infra, per the
the principle of using only infrastructure that is actually needed. A record
that keeps failing past `notification.max-attempts` (default 5) just stays
`FAILED` - there's no dead-letter queue here either, same caveat as above.

`DomainEventConsumer` also listens on `arceus.notification.events.queue`
(bound only to the `user.registered` routing key) and calls this same
pipeline to send the `WELCOME` email whenever auth-service publishes a
`UserRegisteredEvent` - this is the "Register User -> Publish event ->
Notification Service -> Welcome Email" flow.