# scheduler-service

The reusable job scheduling platform every Arceus service delegates
one-time, delayed, cron, and recurring background work to, instead of
rolling its own `@Scheduled` methods or bespoke retry logic.

## Why this exists

Today, any service that needs "run this in 10 minutes" or "run this every
night at 2am" would reach for `@Scheduled`/`@Async` directly and invent its
own retry policy, its own status tracking, its own dead-letter handling.
That works until you need to see every scheduled job across the platform in
one place, or pause one without redeploying, or guarantee a failed job
actually gets retried with backoff instead of silently vanishing.
scheduler-service is the one place that owns "when does this run and what
happens if it fails," exposed identically over REST, gRPC, and RabbitMQ.

**A job's actual work never runs inside scheduler-service.** Every job
declares an `executionTarget` of either `HTTP_CALLBACK` (scheduler-service
POSTs the job's payload to a URL when due) or `MESSAGE_PUBLISH`
(scheduler-service publishes the payload to a RabbitMQ exchange/routing key
when due) - the owning service's own handler does the rest. This is what
makes the service reusable by anyone without scheduler-service needing to
know anything about session cleanup, cache warmup, deployment rollbacks, or
whatever else a job is actually for.

## Job lifecycle

```
                 ┌──────────┐
   create() ───► │SCHEDULED │◄────────────────────┐
                 └────┬─────┘                     │ resume()
                      │ due                        │
                      ▼                       ┌────┴───┐
                 ┌──────────┐    pause()      │ PAUSED │
                 │ RUNNING  │───────────────► └────────┘
                 └────┬─────┘
           succeeds ┌─┴─┐ fails
                     ▼   ▼
            ┌────────────┐   ┌──────────┐  retries left   ┌──────────┐
            │ COMPLETED* │   │  FAILED  │◄─────────────────│ RETRYING │
            └────────────┘   └──────────┘  exhausted ────► (dead-letter)
```

\* Recurring jobs (CRON/FIXED_RATE/FIXED_DELAY) return to `SCHEDULED` with a
freshly computed `nextFireAt` instead of going terminal. A job sitting
`PENDING`/`SCHEDULED`/`PAUSED` past its own `expiresAt` becomes `EXPIRED`
instead, and any status but `RUNNING` can be `CANCELLED`.

## Triggers

| Trigger | Fires |
|---|---|
| `IMMEDIATE` | As soon as the poller's next tick sees it |
| `DELAY` | Once, `delayMs` after creation |
| `CRON` | Repeatedly, per a standard (seconds-optional) cron expression |
| `FIXED_RATE` | Repeatedly, every `fixedRateMs`, measured from the previous *scheduled* time |
| `FIXED_DELAY` | Repeatedly, `fixedDelayMs` after the previous run *completes* |

## Retry & dead-letter

Every job carries its own `maxRetries`, `retryDelayMs`, `backoffMultiplier`,
and `exponentialBackoff` (falling back to `arceus.scheduler.retry.*`
defaults when not specified). A failed attempt under `maxRetries` moves to
`RETRYING` with `nextFireAt` pushed out by the computed backoff (capped by
`arceus.scheduler.retry.max-backoff-ms`); once exhausted, the job goes
`FAILED` and a row is written to the dead-letter table
(`job_dead_letters`) - inspectable, not automatically retried from there.

## REST API

```
POST   /jobs                 register a new job
GET    /jobs/{id}             fetch one job
GET    /jobs                  list, filterable by ?serviceName=&status=, paginated
GET    /jobs/{id}/history      per-attempt execution history for a job
PUT    /jobs/{id}             update mutable fields (payload/priority/retry policy/expiry)
DELETE /jobs/{id}             cancel (idempotent)
POST   /jobs/{id}/pause
POST   /jobs/{id}/resume
POST   /jobs/{id}/cancel
POST   /jobs/{id}/retry        request an immediate retry of a FAILED/RETRYING job
GET    /jobs/stats             live counts by status, dead-letter count, worker pool utilization
GET    /health                 scheduler/worker/RabbitMQ/database status
```

Example `POST /jobs` (an HTTP-callback cron job):

```json
{
  "jobName": "session-cleanup",
  "serviceName": "auth-service",
  "triggerType": "CRON",
  "cronExpression": "0 0 3 * * *",
  "executionTarget": "HTTP_CALLBACK",
  "callbackUrl": "http://auth-service:8081/internal/jobs/session-cleanup",
  "payload": "{\"olderThanDays\": 30}",
  "maxRetries": 5,
  "retryDelayMs": 60000,
  "exponentialBackoff": true
}
```

Or the same job delivered as a RabbitMQ message instead of an HTTP call:

```json
{
  "jobName": "cache-warmup",
  "serviceName": "cache-service",
  "triggerType": "FIXED_RATE",
  "fixedRateMs": 3600000,
  "executionTarget": "MESSAGE_PUBLISH",
  "targetExchange": "arceus.events.exchange",
  "targetRoutingKey": "cache.warmup.requested",
  "payload": "{\"namespace\": \"user\"}"
}
```

## gRPC API

`SchedulerGrpcService` in
`shared/arceus-grpc-contracts/src/main/proto/scheduler.proto` exposes
`ScheduleJob`, `CancelJob`, `PauseJob`, `ResumeJob`, `RetryJob`, `GetJob`,
`ListJobs`, `Ping` - the typed, in-cluster path for other services that
don't want to go through the REST API. Same engine underneath
(`JobService`), so behavior matches the REST API exactly. Every RPC except
`Ping` requires the `x-internal-token` metadata key (see Security below).

## RabbitMQ

**Published** (onto the platform-wide `arceus.events.exchange` - the same
bus auth-service's domain events use, so any consumer bound with `#`,
such as the Gateway's live-events stream, sees every job lifecycle transition):
`job.scheduled`, `job.started`, `job.completed`, `job.failed`,
`job.cancelled`, `job.expired`.

**Consumed** (scheduler-service's own internal queues - see
`shared/arceus-events`'s `SchedulerMessagingTopology`):
- **Retry requests** - `POST /jobs/{id}/retry` and the `RetryJob` RPC don't
  mutate the job directly; they publish a retry-request message that
  `RetryRequestConsumer` applies asynchronously. Any other platform service
  can publish the same message directly, without going through
  scheduler-service's REST/gRPC surface at all.
- **Delayed execution** - `DELAY`-triggered jobs get a low-latency
  "ready" nudge via a RabbitMQ dead-letter-queue delay pattern
  (per-message TTL, no direct consumer on the delay queue itself) so a
  short delay doesn't have to wait for the next database poll tick. The
  database poller remains the source of truth either way - this is purely
  a latency optimization (`arceus.scheduler.delay.enabled`).

## Security

Internal platform service - not fronted by the Gateway. Every REST
endpoint except `/health` and Swagger, and every gRPC call except `Ping`,
requires a shared secret via the `X-Internal-Token` header (REST) or
`x-internal-token` metadata key (gRPC), configured as
`arceus.scheduler.internal.api-token` and matched byte-for-byte on every
calling service's side. This only holds because scheduler-service's ports
aren't reachable from outside the platform's internal network - see
`InternalTokenAuthFilter`'s javadoc.

## Configuration

See `arceus.scheduler.*` in `application.yml`: poll interval and batch
size, worker pool sizing, retry defaults and backoff cap, HTTP callback
timeouts, the delay-nudge toggle, and the internal auth token. Postgres and
RabbitMQ connection settings come from the same `DB_*`/`RABBITMQ_*` env
vars every other Arceus service uses (own database: `arceus_scheduler`).

## Running it

Needs: Postgres, RabbitMQ (same instances as the other services - see the
root `.env`).

```bash
./mvnw -pl services/scheduler-service spring-boot:run
```

REST on `:8086`, gRPC on `:9086` (`SCHEDULER_GRPC_PORT`), Swagger UI at
`http://localhost:8086/swagger-ui.html`.

## Deliberately not built yet

Per the brief: no distributed scheduling, no leader election, no Quartz
clustering, no Kubernetes CronJobs, no workflow orchestration. Every public
REST/gRPC/RabbitMQ contract is designed so these can be layered on without
changing them:

- **Distributed scheduling / leader election** - `JobPollerService`'s
  `@Scheduled` poll loop is the one seam a future leader-elected or
  Kubernetes-native poller would replace. `JobRepository.findDueJobs`
  already uses a `PESSIMISTIC_WRITE` row lock, so even running this from
  more than one instance today wouldn't double-dispatch a job - it just
  isn't the intended topology yet.
- **Quartz clustering** - `TriggerCalculator` isolates every bit of
  cron/rate/delay math into one class; swapping it for Quartz's
  `CronTrigger`/`JobStore` would touch that one class and the `Job` entity's
  columns, not the REST/gRPC/messaging surfaces above it.
- **Workflow orchestration (job chaining/DAGs)** - nothing here assumes a
  job is standalone, but nothing enforces it either; a `parentJobId` column
  plus a "trigger job B on job A's completion" listener on
  `JobLifecycleEventPublisher`'s existing `job.completed` event is the
  natural next step, without changing how a single job is defined.
- **New execution targets** - `JobExecutor` is a one-method strategy
  interface with one implementation per `ExecutionTarget` (`HTTP_CALLBACK`,
  `MESSAGE_PUBLISH`); a gRPC-callback or SQS target is one more enum value
  and one more `@Component` implementing it.
