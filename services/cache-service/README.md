# cache-service

The reusable, Redis-backed caching platform every Arceus service talks to
instead of opening its own Redis connection - see roadmap Phase 3.

## Why this exists

Today, any service that wants a cache would reach for Redis directly and
invent its own key format, its own TTL conventions, its own eviction logic.
That works until two services collide on a key prefix, or one service's
"clear the cache" script deletes another service's keys. cache-service is
the one place that owns Redis, so every caller gets the same namespacing,
TTL policies, serialization, and stats for free, and the operational
surface (connection pool, eviction, memory limits) lives in one service
instead of N copies of it.

## Namespaces

Every operation belongs to a namespace - `auth`, `session`, `otp`, `user`,
`notification`, `ratelimit` by default (see
`arceus.cache.namespaces.allowed` in `application.yml`). This is what makes
namespace-wide eviction (`DELETE /cache/namespace/{namespace}`) safe: it can
only ever touch one team's keys.

## Storage layout

Every entry is two Redis keys, written and expired together:

- `{namespace}:{key}` - the actual data, as a native Redis String / Hash /
  List / Set / Sorted Set depending on `type`.
- `{namespace}:{key}:__meta` - a small hash recording `type`,
  `serialization`, `compressed`, `ttlPolicy`, `ttlSeconds`, `cachePolicy`,
  `createdAt`, `updatedAt`. A bare `GET` has nowhere else to learn which
  native structure to read from or how the bytes were encoded, and this is
  also what makes `SLIDING` TTL renewal possible (see below).

## TTL policies

| Policy | Behavior |
|---|---|
| `NO_EXPIRY` | Persisted; never expires until deleted/evicted |
| `ABSOLUTE` | Expires at a caller-given instant (`absoluteExpireAt`), fixed |
| `SLIDING` | Expires `ttlSeconds` after the *last access* - every GET renews it |
| `CUSTOM` | Expires `ttlSeconds` after the write, fixed, never renewed |

## Cache policies

`cachePolicy` (`CACHE_ASIDE` / `WRITE_THROUGH` / `WRITE_AROUND`) is recorded
on the entry's metadata for observability, but doesn't change how a write is
executed here. This service has no idea what a caller's source-of-truth
database is - true write-through/write-around is a contract the *calling*
service implements against its own store; this field just lets you audit
"why is this namespace mostly write-arounds?" later. It's a natural
extension point if cache-service ever grows a refresh-ahead feature that
needs to know the contract, but nothing here fakes extra behavior today.

## REST API

```
POST   /cache                              create/overwrite (Set Value)
GET    /cache/{namespace}/{key}             read
PUT    /cache/{namespace}/{key}             update (404 if it doesn't exist)
DELETE /cache/{namespace}/{key}             delete one key

GET    /cache/{namespace}/{key}/exists
GET    /cache/{namespace}/{key}/ttl
POST   /cache/{namespace}/{key}/expire      { "ttlSeconds": 120 }
POST   /cache/{namespace}/{key}/increment   { "delta": 1 }   (delta optional, defaults 1)
POST   /cache/{namespace}/{key}/decrement   { "delta": 1 }

PUT    /cache/{namespace}/{key}/hash        { "field": "...", "value": ... }
GET    /cache/{namespace}/{key}/hash/{field}
DELETE /cache/{namespace}/{key}/hash/{field}

POST   /cache/bulk/get                      { "keys": [{"namespace":"...","key":"..."}] }
POST   /cache/bulk/put                      { "entries": [ <same body as POST /cache> ] }

DELETE /cache/namespace/{namespace}         evict a whole namespace
DELETE /cache/pattern/{pattern}             evict a namespace-scoped glob, e.g. session:user:*

GET    /cache/stats
GET    /health
```

Example `POST /cache`:

```json
{
  "namespace": "session",
  "key": "abc123",
  "value": { "userId": 42, "roles": ["admin"] },
  "type": "STRING",
  "serialization": "JSON",
  "ttlPolicy": "SLIDING",
  "ttlSeconds": 1800
}
```

`type` controls the shape `value` must take:

- `STRING` - any JSON value (or plain text/Base64 for `STRING`/`BINARY` serialization)
- `HASH` - a JSON object, `{"field": value, ...}`
- `LIST` - a JSON array, order preserved
- `SET` - a JSON array of unique members
- `SORTED_SET` - a JSON array of `{"member": ..., "score": 1.0}`

## gRPC API

`CacheGrpcService` in `shared/arceus-grpc-contracts/src/main/proto/cache.proto`
exposes `Put`, `Get`, `Delete`, `Exists`, `Expire`, `Increment`, `HashSet`,
`HashGet`, `BulkGet`, `BulkPut`, `Ping` - the typed, in-cluster path for
other services (Gateway, Auth, Notification, ...) that don't want to go
through the REST API. Same engine underneath (`CacheStoreService`), so
behavior matches the REST API exactly.

## Serialization & compression

- `JSON` (default) - value is JSON text, stored as UTF-8 bytes verbatim (validated on write)
- `STRING` - value is plain text, stored as UTF-8 bytes verbatim
- `BINARY` - value is Base64 text on the wire; decoded to raw bytes before storage

Compression (gzip) only applies to `STRING`-type entries - collection types
are typically many small elements, where per-element gzip overhead isn't
worth it. Set `"compress": true` to force it, or let
`arceus.cache.compression.auto-threshold-bytes` (default 8 KiB) trigger it
automatically. `arceus.cache.max-object-size-bytes` (default 5 MiB) rejects
anything larger with `413 Payload Too Large`, checked against the
serialized-but-not-yet-compressed size.

## Statistics

`GET /cache/stats` returns hit/miss/eviction counters (Redis `INCR`
counters, shared across every cache-service instance and surviving
restarts) plus live Redis-level numbers: `totalKeys` (`DBSIZE`),
`memoryUsageBytes` (`INFO memory: used_memory`), `expiredKeys`
(`INFO stats: expired_keys`). The last three are global to the configured
Redis logical database, not filtered to keys this service wrote - Redis
doesn't track that distinction without keyspace-notification Pub/Sub, which
is deliberately out of scope for now (see below).

## Configuration

See `arceus.cache.*` in `application.yml`: Redis connection pool sizing,
`default-ttl-seconds`, `default-serialization`, `max-object-size-bytes`,
compression, and the namespace allow-list. Redis host/port/password come
from `spring.data.redis.*` (same `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`
env vars every other Arceus service uses).

## Running it

Needs: Redis (same instance as the other services - see the root `.env`).

```powershell
.\mvnw.cmd -pl services/cache-service spring-boot:run
```

REST on `:8085`, gRPC on `:9085` (`CACHE_GRPC_PORT`), Swagger UI at
`http://localhost:8085/swagger-ui.html`.

## Deliberately not built yet

Per the brief: no Redis Cluster, no Sentinel, no Pub/Sub, no Streams, no
distributed locking. The package structure keeps these as clean additions
rather than rewrites:

- **Redis Cluster/Sentinel** - would live entirely inside `RedisConfig`;
  nothing in `CacheStoreService` assumes a standalone topology.
- **Pub/Sub / keyspace notifications** - would tighten up `expiredKeys` to
  be exact and per-namespace instead of the current Redis-global INFO
  approximation, and could drive real-time cache invalidation across
  instances later.
- **Distributed locking** - `IncrementRequest`/`incrementBy` already relies
  on Redis's own atomic `INCRBY`; a `Lock`/`Unlock` RPC pair would be a
  natural sibling to `CacheGrpcService` once needed, backed by a new
  `core/`-level component, without touching the existing operations.
- **Postgres metadata store** - the brief calls Postgres "optional, for
  metadata only." Nothing here pulls in `spring-boot-starter-data-jpa` or
  a Postgres driver today - Spring Boot auto-configures a `DataSource` the
  moment those are on the classpath (regardless of whether anything uses
  it), so adding them without a real entity would just break startup, not
  sit inertly. The natural next step, if/when it's needed, is to add
  `spring-boot-starter-data-jpa` + `postgresql`, configure a real
  `spring.datasource.*`, and add a `NamespaceConfig` entity/repository for
  admin-managed per-namespace defaults (default TTL, description, owning
  team), read through a thin service that falls back to the in-memory
  `application.yml` list used today when persistence isn't configured.
