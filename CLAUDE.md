# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

WTSend backend — a real-time chat/messaging API. Spring Boot 4.0.x, Java 21, Maven. Base package `com.wtsend.backend`.

## Commands

```bash
./mvnw spring-boot:run                                  # run the app (needs MySQL + Redis up)
docker compose up -d                                    # start Redis (localhost:6379)
./mvnw clean package                                    # build jar (runs tests)
./mvnw test                                             # all tests
./mvnw test -Dtest=BackendApplicationTests#methodName   # single test
```

There is no real test suite — only `BackendApplicationTests#contextLoads`, which boots the whole app and so needs MySQL + Redis reachable. `./mvnw clean package -DskipTests` if they aren't.

MySQL must exist separately (DB `wtsend`); Hibernate `ddl-auto=update` creates/updates tables. Active profile is `local` (`application-local.properties`), which points the datasource at a Docker MySQL IP — override `spring.datasource.url` for your local DB.

## Architecture

Standard layered Spring MVC: `controllers/` → `services/` (impls + `services/interfaces/I*`) → `repository/` (Spring Data JPA) → `model/` (JPA entities). Requests/responses are DTOs in `dto/request` and `dto/response`; entity↔DTO conversion uses ModelMapper (`configs/ModelMapperConfig`) plus hand-written mappers in `libs/`. Controllers wrap responses in `dto/response/ApiResponse`; errors go through `exceptions/GlobalExceptionHandler`.

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

> Branch `impl-stomp` is named for a planned migration to STOMP, but **no STOMP/Spring-WebSocket code exists yet** — there is no `spring-boot-starter-websocket` dependency and no broker config. netty-socketio is the only live mechanism. Re-check before assuming otherwise.

## Config & secrets

Config comes entirely from `application.properties` + `application-local.properties`. A `.env` and a `java-dotenv` dependency exist but **nothing in the code reads them** — don't assume a value lands in the app just because it's in `.env`.

`application-local.properties` holds real credentials (DB password, Cloudinary, Resend, Google/Facebook OAuth, RSA key paths) but is **gitignored and has never been committed** — `git log --all -- src/main/resources/application-local.properties` is empty, as it is for `.env`, `*.pem`, and `certs/`. The only tracked config is `application.properties`, which contains just the app name and active profile. Keep it that way: never move a credential into a tracked file, and don't echo their values.

CORS origins come from `app.cors.allowed-origin-patterns` (local profile only). It defaults to empty, so an unconfigured profile allows no cross-origin traffic — deliberate, since `allowCredentials(true)` plus a wildcard pattern is exploitable.

External services: **Cloudinary** (media upload, `CloudinaryService`), **Resend** + Spring Mail with Thymeleaf templates in `resources/templates/emails/` (verification & password-reset email).
