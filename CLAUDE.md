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

`MessageServiceTest`, `UserServiceTest` and `ErrorCodeTest` are plain Mockito/JUnit tests and run with no infrastructure. `BackendApplicationTests#contextLoads` boots the whole app, so it needs MySQL + Redis reachable — use `./mvnw clean package -DskipTests` when they aren't.

`docker-compose.yml` brings up both MySQL and Redis; Hibernate `ddl-auto=update` then creates the tables inside the `wtsend` database the image creates on first boot. Active profile is `local` (`application-local.properties`), pointing at `localhost:3306`.

Compose reads `backend/.env` (gitignored) for `MYSQL_PASSWORD` and uses it as the MySQL root password, so it has to stay equal to `spring.datasource.password`. The app connects as `root` — don't add `MYSQL_USER=root`, the official image rejects that. MySQL runs with `--default-time-zone=+00:00` because every timestamp in the app is an `Instant`.

## Architecture

Standard layered Spring MVC: `controllers/` → `services/` (impls + `services/interfaces/I*`) → `repository/` (Spring Data JPA) → `model/` (JPA entities). Requests/responses are DTOs in `dto/request` and `dto/response`; entity↔DTO conversion uses ModelMapper (`configs/ModelMapperConfig`) plus hand-written mappers in `libs/`. Controllers return bare DTOs or `ResponseEntity<Object>` — `dto/response/ApiResponse` exists but **nothing references it**. Errors go through `common/exception/GlobalExceptionHandler`, which renders an `ErrorResponse` from an `ErrorCode`; those numeric codes are a stable contract with the frontend, so never reuse or renumber one.

Two things here are non-obvious and cut across many files:

### Auth is stateless JWT via OAuth2 resource server — not a custom filter

- Tokens are **RS256**, signed/verified with RSA keys at `classpath:certs/{private,public}.pem` (wired in `configs/security/JwtConfig` + `RsaKeyConfig`). `JwtService` issues them; access-token TTL is 5 min, refresh 14 (units are minutes/days per config, treated as minutes in code).
- Spring's `oauth2ResourceServer().jwt()` validates every request — **`JwtAuthenticationFilter` is fully commented out and `EmailVerificaionFilter` is never registered**; both are dead. Don't resurrect them, add auth logic in `JwtConfig`/`SecurityConfig` instead.
- **Authorization is driven by the `emv` (email-verified) JWT claim.** `JwtConfig.jwtAuthenticationConverter` maps `emv` → authority `EMAIL_VERIFIED` or `EMAIL_NOT_VERIFIED`. `SecurityConfig` gates `/api/**` on `EMAIL_VERIFIED`, with narrow exceptions (`/api/users/me`, `/api/resend-otp` need only auth; `/api/send-verify-email` needs `EMAIL_NOT_VERIFIED`). If you add an endpoint, know which authority tier it falls under.
- **Refresh tokens live in Redis** (`RefreshTokenService` / `RefreshTokenRepository`), as do OTPs and email-verification tokens. Redis is required, not optional.
- Google login: `GoogleAuthService` verifies the Google ID token, then `AuthService.googleLogin` finds-or-creates the user.

### Real-time is netty-socketio, a SEPARATE server — not Spring WebSocket

- `socket/` runs a standalone Socket.IO server (netty-socketio) on **port 9092**, started by `SocketIOServerRunner` (`CommandLineRunner`) alongside the Spring web port. It is a distinct server, not a Spring MVC endpoint.
- Socket auth: client passes the JWT as a `?token=` handshake query param; `SocketIOService.onConnect` decodes it with the same `JwtDecoder`. Rooms are named by **conversation id**; on connect a user is auto-joined to all their conversation rooms plus a room keyed by their own user id. Online presence is tracked in-memory in `OnlineUserStore`.
- **Messages are pushed after the DB commit, not inline.** Saving a message publishes a `NewMessageEvent`; `listener/MessageNotificationListener` handles it with `@TransactionalEventListener(AFTER_COMMIT)` and broadcasts `new-message` to the conversation room. Preserve this event→listener→broadcast flow — don't call the socket server directly from a service inside the transaction.

> Branch `impl-stomp` is named for a migration to STOMP that was **abandoned and removed**. A `configs/WebSocketConfiguration` + `configs/WebSocketHandshake` pair briefly existed (broker on `/ws`, plus a handshake interceptor that authenticated nobody) with zero `@MessageMapping` handlers; both files and the `spring-boot-starter-websocket` dependency are gone. netty-socketio is the only real-time mechanism — don't reintroduce STOMP alongside it.

## Config & secrets

Config comes entirely from `application.properties` + `application-local.properties`. A `.env` and a `java-dotenv` dependency exist but **nothing in the code reads them** — don't assume a value lands in the app just because it's in `.env`.

`application-local.properties` holds real credentials (DB password, Cloudinary, Resend, Google/Facebook OAuth, RSA key paths) but is **gitignored and has never been committed** — `git log --all -- src/main/resources/application-local.properties` is empty, as it is for `.env`, `*.pem`, and `certs/`. The only tracked config is `application.properties`, which contains just the app name and active profile. Keep it that way: never move a credential into a tracked file, and don't echo their values.

CORS origins come from `app.cors.allowed-origin-patterns` (local profile only). It defaults to empty, so an unconfigured profile allows no cross-origin traffic — deliberate, since `allowCredentials(true)` plus a wildcard pattern is exploitable.

External services: **Cloudinary** (media upload, `CloudinaryService`), **Resend** + Spring Mail with Thymeleaf templates in `resources/templates/emails/` (verification & password-reset email).
