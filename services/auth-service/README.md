# auth-service

Owns identity: registration, login, JWTs, refresh tokens, Google/GitHub
OAuth2, OTP verification, and account management (password reset, change
password/email/phone). It does **not** send any email or SMS itself:
OTPs are published to RabbitMQ and delivered by `notification-service`.

## Endpoints

| Method | Path | Auth | What it does |
|---|---|---|---|
| POST | `/api/auth/register` | no | Creates account (unverified), triggers email + SMS OTPs |
| POST | `/api/auth/verify-email` | no | Confirms email |
| POST | `/api/auth/verify-phone` | no | Confirms phone |
| POST | `/api/auth/login` | no | Checks password, triggers login OTP email |
| POST | `/api/auth/verify-login-otp` | no | Returns real tokens |
| POST | `/api/auth/forgot-password` | no | Sends reset code to email |
| POST | `/api/auth/verify-forgot-password-otp` | no | Returns a one-time reset token |
| POST | `/api/auth/reset-password` | no | Sets new password using that token, revokes all refresh tokens |
| POST | `/api/auth/change-password` | yes | Change password (needs current password) |
| POST | `/api/auth/change-email/request` | yes | Step 1: send code to new email |
| POST | `/api/auth/change-email/verify` | yes | Step 2: confirm, email updated |
| POST | `/api/auth/change-phone/request` | yes | Step 1: send code to new phone |
| POST | `/api/auth/change-phone/verify` | yes | Step 2: confirm, phone updated |
| POST | `/api/auth/refresh` | no | Rotates a refresh token |
| POST | `/api/auth/oauth2/exchange` | no | Exchanges an OAuth2 login code for tokens |
| POST | `/api/auth/logout` | no | Revokes a refresh token |
| GET | `/api/auth/me` | yes | Current user profile |
| GET/DELETE | `/api/auth/sessions[/{id}]` | yes | List / revoke your own sessions |
| GET/POST/DELETE | `/api/auth/api-keys[/{id}]` | yes | Personal API keys (`/{id}/reveal` shows one again) |
| GET/POST/DELETE | `/api/auth/organizations/{org}/projects/{project}/api-keys` | yes | Project API keys |
| GET/PATCH/DELETE | `/api/auth/users/**` | admin | List users, change roles/status, manage their sessions |
| GET/POST/PUT/PATCH/DELETE | `/api/auth/permissions`, `/api/auth/roles` | admin | RBAC management |

Swagger UI: `http://localhost:8081/swagger-ui.html`

## Running it

Needs: Postgres, Redis, RabbitMQ, and **notification-service running** (or
OTP emails/SMS just never arrive - the code still works, the message just
sits on the queue until something consumes it). See the root README for
how to start the whole stack.

## Design notes worth knowing about

- **Rate limiting** on all the endpoints attackers actually care about
  (register, login, every OTP verify, forgot-password, change-email/phone)
  via `shared/arceus-ratelimit`'s `@RateLimited` annotation - Redis-backed,
  no separate service. See the module's Javadoc for how per-IP vs
  per-identifier rules combine. `X-Forwarded-For` is honored for IP
  resolution, which means it's only trustworthy once gateway-service is the
  sole public entry point and sets that header itself - revisit then.
- **Account linking by email** for OAuth2 users is a known tradeoff - fine
  for now, would need email-verification-before-linking in a real product.
- **contextLoads test** needs real Redis + RabbitMQ available to pass now.
  Testcontainers would fix this properly later.
- **CORS** is handled at gateway-service (`GATEWAY_CORS_ALLOWED_ORIGINS`).
- **No dead-letter queue** yet for failed OTP deliveries - a bad email/SMS
  currently just gets logged and dropped by notification-service.
- **Super admin bootstrap**: set `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` before first start;
  an existing account is never modified.
