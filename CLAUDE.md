# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

WTSend backend — a real-time chat/messaging API. Spring Boot 4.1.x, Java 21, Maven. Base package `com.wtsend.backend`.

## Commands

```bash
./mvnw spring-boot:run                                  # run the app (needs MySQL + Redis up)
docker compose up -d --wait                             # MySQL (3306) + Redis (6379)
./mvnw clean package                                    # build jar (runs tests)
./mvnw test                                             # all tests
./mvnw test -Dtest=BackendApplicationTests#methodName   # single test
```

Most tests are plain Mockito/JUnit and need no infrastructure. Four need MySQL and Redis reachable, because they boot the context: `BackendApplicationTests`, `ConversationQueryCountTest`, `ConversationBroadcastCommitTest`, `UnverifiedAccountCleanupJobTest`. Use `./mvnw clean package -DskipTests` when the stack is down.

**A second Spring context in the same JVM collides on socket port 9092**, since `SocketIOServerRunner` binds it from a `CommandLineRunner` and the first context never releases it — the failure reads `Failed to execute CommandLineRunner`. Any test whose configuration forks a new context must either mock `SocketIOServer` or set `socketio.port=0`; the existing context-booting tests do one or the other.

`docker-compose.yml` brings up both MySQL and Redis. Active profile is `local` (`application-local.properties`), pointing at `localhost:3306`. Compose derives the container names from the project, so address them as `docker compose exec mysql …`, not `docker exec mysql …`.

**Flyway owns the schema**, not Hibernate: `ddl-auto=validate`, migrations in `src/main/resources/db/migration`. `V1__baseline.sql` is the schema `ddl-auto=update` used to generate, captured from a live database and verified by dropping the schema and letting Flyway rebuild it. Add a column by writing a migration — Hibernate will refuse to start if the entities and the schema disagree, which is the point.

The dependency that matters is `spring-boot-flyway`, not `flyway-core`. Boot 4 splits autoconfiguration per technology, so `flyway-core` alone resolves and compiles and then silently runs no migration at all.

**`spring.jpa.open-in-view=false`.** Any read path that walks a lazy association needs `@Transactional(readOnly = true)` and a fetch graph; with OSIV on, those loads happened during response rendering and an N+1 was invisible. `ConversationRepository.findConversations`/`findByIdWithGraph` fetch everything `ConversationMapper` touches — `ConversationQueryCountTest` pins the query count so a regression fails rather than just getting slower.

Compose reads `backend/.env` (gitignored) for `MYSQL_PASSWORD` and uses it as the MySQL root password, so it has to stay equal to `spring.datasource.password`. The app connects as `root` — don't add `MYSQL_USER=root`, the official image rejects that. MySQL runs with `--default-time-zone=+00:00` because every timestamp in the app is an `Instant`.

## Architecture

Standard layered Spring MVC: `controllers/` → `services/` (impls + `services/interfaces/I*`) → `repository/` (Spring Data JPA) → `model/` (JPA entities). Requests/responses are DTOs in `dto/request` and `dto/response`; entity↔DTO conversion uses ModelMapper (`configs/ModelMapperConfig`) plus hand-written mappers in `libs/`. Controllers return bare DTOs or `ResponseEntity<Object>` — `dto/response/ApiResponse` exists but **nothing references it**. Errors go through `common/exception/GlobalExceptionHandler`, which renders an `ErrorResponse` from an `ErrorCode`; those numeric codes are a stable contract with the frontend, so never reuse or renumber one.

Two things here are non-obvious and cut across many files:

### Auth is stateless JWT via OAuth2 resource server — not a custom filter

- Tokens are **RS256**, signed/verified with RSA keys at `classpath:certs/{private,public}.pem` (wired in `configs/security/JwtConfig` + `RsaKeyConfig`). `JwtService` issues them; access-token TTL is 5 min, refresh 14 (units are minutes/days per config, treated as minutes in code).
- Spring's `oauth2ResourceServer().jwt()` validates every request — **`JwtAuthenticationFilter` is fully commented out and `EmailVerificaionFilter` is never registered**; both are dead. Don't resurrect them, add auth logic in `JwtConfig`/`SecurityConfig` instead.
- **Authorization is driven by the `emv` (email-verified) JWT claim.** `JwtConfig.jwtAuthenticationConverter` maps `emv` → authority `EMAIL_VERIFIED` or `EMAIL_NOT_VERIFIED`. `SecurityConfig` gates `/api/**` on `EMAIL_VERIFIED`, with narrow exceptions (`/api/users/me`, `/api/resend-otp` need only auth; `/api/send-verify-email` needs `EMAIL_NOT_VERIFIED`). If you add an endpoint, know which authority tier it falls under.
- **Refresh tokens live in Redis** (`RefreshTokenService` / `RefreshTokenRepository`), as do OTPs and email-verification tokens. Redis is required, not optional — it also backs the ShedLock lock for scheduled jobs.
- Google login: `GoogleAuthService` verifies the Google ID token, then `AuthService.googleLogin` finds-or-creates the user.
- **The email address is not updatable, and that is load-bearing.** `UpdateUserRequest` has no email field. Changing it used to reset `emailVerified`, which gave the flag two meanings — never verified, versus verified once and now changing address — and `UnverifiedAccountCleanupJob` deletes anything unverified 24h after `createdAt`, so an established account would have been deleted a day after changing its email. It also let anyone move an account onto an address they do not own, which is what that job exists to prevent. Don't reintroduce it without a separate "verified once" marker.
- Verification mail is **not sent inline**: `signUp` and `signIn` publish `VerificationEmailRequestedEvent` and `VerificationEmailListener` calls Resend after commit. `signUp` is `@Transactional` and used to hold a pooled connection for the whole third-party request. The listener logs failures rather than rethrowing — partly because there is nothing to roll back after commit, partly because `sendVerifyLink` throws `VERIFY_EMAIL_COOLDOWN` within 30s of the previous send and that used to fail the login of an unverified user who simply tried twice. Note this is still synchronous on the request thread; it is the seam a Kafka produce replaces.

### Real-time is netty-socketio, a SEPARATE server — not Spring WebSocket

- `socket/` runs a standalone Socket.IO server (netty-socketio) on **port 9092**, started by `SocketIOServerRunner` (`CommandLineRunner`) alongside the Spring web port. It is a distinct server, not a Spring MVC endpoint.
- Socket auth: client passes the JWT as a `?token=` handshake query param; `SocketIOService.onConnect` decodes it with the same `JwtDecoder`. Rooms are named by **conversation id**; on connect a user is auto-joined to all their conversation rooms plus a room keyed by their own user id. Online presence is tracked in-memory in `OnlineUserStore`.
- **Nothing is pushed from inside a transaction.** A service publishes a domain event; a listener in `listener/` handles it with `@TransactionalEventListener(AFTER_COMMIT)` and does the emitting. `NewMessageEvent` → `new-message`, `ConversationCreatedEvent` → `new-group`, `ConversationReadEvent` → `read-message`. No service holds a `SocketIOServer` any more, and none should: emitting before commit told clients about writes that were not yet durable. Events carry mapped DTOs, never entities — the listener runs with no open session.
  - Publishing from a method with **no** transaction silently discards the event. That is why `createConversation` is `@Transactional`; `VerificationEmailListener` instead opts into `fallbackExecution = true` because `signIn` has no transaction.
- `presence-changed` carries one user id and a flag. The full `online-users` list is sent **only to the arriving socket** — it used to be rebroadcast to everyone on every connect and disconnect. `OnlineUserStore.add`/`remove` report the presence *transition*, so a second tab is not an arrival and closing one of two tabs is not a departure.

> Branch `impl-stomp` is named for a migration to STOMP that was **abandoned and removed**. A `configs/WebSocketConfiguration` + `configs/WebSocketHandshake` pair briefly existed (broker on `/ws`, plus a handshake interceptor that authenticated nobody) with zero `@MessageMapping` handlers; both files and the `spring-boot-starter-websocket` dependency are gone. netty-socketio is the only real-time mechanism — don't reintroduce STOMP alongside it.

## Config & secrets

Config comes entirely from `application.properties` + `application-local.properties`. The `java-dotenv` dependency reads nothing — **no Java code loads `.env`**. Docker Compose does read it, for `MYSQL_PASSWORD`, which therefore has to stay equal to `spring.datasource.password`.

`application-local.properties` holds real credentials (DB password, Cloudinary, Resend, Google/Facebook OAuth, RSA key paths) but is **gitignored and has never been committed** — `git log --all -- src/main/resources/application-local.properties` is empty, as it is for `.env`, `*.pem`, and `certs/`. The only tracked config is `application.properties`, which contains just the app name and active profile. Keep it that way: never move a credential into a tracked file, and don't echo their values.

CORS origins come from `app.cors.allowed-origin-patterns` (local profile only). It defaults to empty, so an unconfigured profile allows no cross-origin traffic — deliberate, since `allowCredentials(true)` plus a wildcard pattern is exploitable.

External services: **Cloudinary** (media upload, `CloudinaryService`), **Resend** + Spring Mail with Thymeleaf templates in `resources/templates/emails/` (verification & password-reset email).

## Two things about Spring Boot 4 that cost time

**Jackson 3.** Boot 4 autoconfigures `tools.jackson.databind.json.JsonMapper`; there is **no** `com.fasterxml.jackson.databind.ObjectMapper` bean. Jackson 2 is still on the classpath only because netty-socketio's `JacksonJsonSupport` needs it, and `SocketIOConfig` builds its own Jackson 2 mapper for that. Inject the Jackson 3 type in application code — asking for the Jackson 2 one fails context startup.

**Autoconfiguration is split per technology.** `spring-boot-jackson`, `spring-boot-flyway`, and so on. Adding the plain library (`flyway-core`) gives you the classes and none of the wiring, and the symptom is silence rather than an error.

## Scheduled work

`@EnableScheduling` is on. `jobs/UnverifiedAccountCleanupJob` deletes registrations left unverified for 24h, hourly per `app.cleanup.unverified-accounts.cron`. Every instance runs its own scheduler, so jobs carry `@SchedulerLock` (ShedLock, Redis-backed, no table — see `configs/SchedulerLockConfig`). The sweep is also written as a single conditional `DELETE`, so a double run removes nothing rather than racing.
