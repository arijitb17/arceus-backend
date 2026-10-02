# Arceus Demo - Consumer App

A single static HTML page that plugs into the platform with nothing but a
project API key - the same way any third-party app built on Arceus would.
No build step, no dependencies. This is separate from `portal/`, which is
the *admin* console for managing the platform - this is what *using* it
looks like.

## Run it

The platform (`docker compose up` or `scripts/run-all.sh`) needs to
already be running - this page just talks to your Gateway at
`http://localhost:8090`.

Serve the file from any static server - opening it directly as a
`file://` URL won't work, since the browser needs a real origin for CORS.
Either of these works with nothing else to install:

```bash
# Python (already on most machines)
python -m http.server 4300 --directory demo

# or Node
npx serve demo -l 4300
```

Then open **http://localhost:4300**.

Port 4300 is already allowed in `GATEWAY_CORS_ALLOWED_ORIGINS` by
default (see `.env.example` / `docker-compose.yml`). If you serve it from
a different port, add that origin there too and restart `gateway-service`.

## Get an API key to paste in

Portal → Organizations → pick your org → pick a project → **API
Keys** tab → Generate key. Paste the raw key into this page's "Connect"
field.

## What each module actually does

- **Authentication** - the one module that needs *no* API key, only the
  Gateway URL, because it's the end-user side of the platform: sign up,
  verify, sign in, and manage a session against auth-service. It walks the
  real flow: `register` -> email + SMS one-time codes (`verify-email`,
  `verify-phone`) -> `login` -> emailed login code (`verify-login-otp`) ->
  access + refresh tokens. Once signed in it shows the profile from
  `GET /api/auth/me`, the decoded JWT claims, a live expiry countdown, and
  buttons for `refresh` (rotates the refresh token), `sessions`, and
  `logout`. Tokens are held in memory only, so reloading the page signs you
  out. Two practical notes: sign-in stays locked until **both** the email
  and SMS codes are confirmed, and the auth endpoints are rate-limited
  (registration: 5 per hour per IP; login: 5 attempts per 15 minutes per
  account) - so hammering it while testing will get you a `429` for a while.
- **Feature Flags** - creates a real `BOOLEAN` flag and toggles it via
  `POST /api/feature-flags/{key}/enable|disable` - changes are live
  immediately, backed by feature-flag-service's Redis-cached evaluation.
- **Cache** - `POST`/`GET /api/cache` against a `demo` namespace, backed
  by real Redis through cache-service.
- **Notifications** - sends a real email via notification-service. Needs
  a template that already exists (create one first in the Portal's
  Notifications → Templates page) - there's no default template to fall
  back to.
- **Scheduler** - read-only. Creating a job needs a real callback target
  to actually fire against, which this demo doesn't have one of - jobs
  are created from the Portal instead.
- **Rate Limiter** - fires 30 requests as fast as the browser can send
  them and shows the raw HTTP status of each one, so you can watch
  `200`s turn into `429`s once you cross `GATEWAY_RATE_LIMIT`.

## Why it can call admin-gated endpoints with just an API key

A project API key inherits the roles of whichever user generated it (see
`ApiKeyAuthFilter`'s javadoc in gateway-service) - so if you generated the
key from the seeded super-admin account, it can reach the same endpoints
that account can. This is a deliberate simplification, not a security
model to take as-is into a real multi-tenant product - a production
version would scope a project key to that project's own data only, not
its creator's platform-wide role.