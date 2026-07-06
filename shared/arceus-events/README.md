# arceus-events

Shared contracts for how auth-service and notification-service talk to each
other and touch the same Redis state - so a typo or drift in one service
can't silently break the other.

Nothing in this module runs on its own. It's a small dependency (records,
enums, and string-constant classes) that both services import, so both
sides are always compiling against the exact same names.

## What's in it

| Class | What it defines |
|---|---|
| `OtpMessagingTopology` | The RabbitMQ exchange, queue, and routing key names. Declared once here, `@Bean`-registered by both services (auth-service declares the exchange only, since it just publishes; notification-service declares the exchange + queue + binding, since it's the one actually consuming). |
| `OtpNotificationMessage` | The message body that travels over RabbitMQ from auth-service → notification-service. A `record`, so it's naturally `Serializable` and immutable. `otpCode` is only populated for `EMAIL` - see the note below. |
| `OtpChannel` | `EMAIL` \| `SMS` |
| `OtpPurpose` | `EMAIL_VERIFICATION`, `PHONE_VERIFICATION`, `LOGIN_2FA`, `FORGOT_PASSWORD`, `EMAIL_CHANGE` - used both to pick the right Redis key namespace and (for email) to pick the right wording in the notification email. |
| `OtpRedisKeys` | Every Redis key format either service reads or writes: `emailOtp()`, `phoneSession()`, `resetToken()`, `pendingEmailChange()`, `pendingPhoneChange()`. |

## Why `otpCode` is sometimes `null`

Email and SMS aren't symmetric, and `OtpNotificationMessage` reflects that
rather than hiding it:

- **Email**: auth-service generates the 6-digit code itself, stores it in
  Redis via `OtpRedisKeys.emailOtp(...)`, and puts the same code in the
  message. notification-service just formats and sends it - it never
  decides what the code is.
- **SMS**: 2Factor.in generates its own OTP when notification-service calls
  its send endpoint - there's nothing for auth-service to generate ahead of
  time, so `otpCode` is `null` for `OtpChannel.SMS` messages. Instead,
  notification-service writes the 2Factor *session id* into Redis under
  `OtpRedisKeys.phoneSession(phoneNumber)`, and auth-service reads that same
  key later when the user submits their code for verification.

That Redis key is the one place the two services touch the same data
without going through RabbitMQ at all - deliberate, since Redis is shared
infrastructure both already have a legitimate direct connection to, not a
service-to-service call.

## Adding a new OTP purpose or channel

1. Add the new value to `OtpPurpose` (or `OtpChannel`).
2. If it's a purpose used in emails, add a case to `OtpConsumer.purposeLabel()`
   in notification-service - the compiler will remind you, since that
   `switch` has no `default` branch on purpose.
3. If it needs its own Redis key shape, add a method to `OtpRedisKeys`
   rather than building the key string inline anywhere else - that's the
   entire point of this module.

## Depending on this module

```xml
<dependency>
    <groupId>com.arijit</groupId>
    <artifactId>arceus-events</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Run `.\mvnw.cmd install -DskipTests` from the project root at least once so
this jar exists in your local Maven repo before a service that depends on
it can resolve it.