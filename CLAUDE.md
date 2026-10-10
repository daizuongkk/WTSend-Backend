# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

WTSend backend — a real-time chat/messaging API. Spring Boot 4.1.x, Java 21, Maven. Base package `com.wtsend.backend`.

## Commands

```bash
./mvnw spring-boot:run                                  # run the app (needs MySQL + Redis up)
docker compose up -d --wait                              # MySQL 3306, Redis 6379, Kafka 29092, kafka-ui 8090
./mvnw clean package                                    # build jar (runs tests)
./mvnw test                                             # all tests
./mvnw test -Dtest=BackendApplicationTests#methodName   # single test
```

Most tests are plain Mockito/JUnit and need no infrastructure. Six boot the context and so need MySQL and Redis reachable: `BackendApplicationTests`, `ConversationQueryCountTest`, `ConversationBroadcastCommitTest`, `UnverifiedAccountCleanupJobTest`, `RedisPresenceStoreTest`, `SocketRelayChannelTest`, `RequestDeserializationTest`. `EmailNotificationFlowTest` needs neither -- it is a slice over an embedded broker, and `@EnableAutoConfiguration` there would drag in the datasource and defeat the point. Use `./mvnw clean package -DskipTests` when the stack is down.

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
- **Provider logins are keyed on the provider's subject id, in `user_identity` — never on the email address.** `AuthService.resolveProviderAccount` is the single door both Google and Facebook go through. A subject it has not seen links onto an existing account *only* when the provider says it verified that address, otherwise `PROVIDER_LINK_NOT_ALLOWED`; matching on email alone meant anyone who could make a provider report a victim's address signed into the victim's account. Facebook passes `providerVerifiedEmail = false` because Graph exposes no verified flag, so a Facebook sign-up starts unverified and verifies by mail like any other. `V6__user_identity.sql` replaced the single `users.auth_provider` column, which could only record how an account was first created; its backfilled rows carry a NULL subject and heal on the next matching login.
  - `user_identity` has `ON DELETE CASCADE` on purpose: `UnverifiedAccountCleanupJob` deletes through a bulk JPQL `DELETE` that bypasses every JPA cascade, and unverified Facebook accounts are exactly what it sweeps.
- An unverified account gets an access token and **no** refresh token, from `AuthService.session` — the one place every sign-in builds its response.
- **The email address is not updatable, and that is load-bearing.** `UpdateUserRequest` has no email field. Changing it used to reset `emailVerified`, which gave the flag two meanings — never verified, versus verified once and now changing address — and `UnverifiedAccountCleanupJob` deletes anything unverified 24h after `createdAt`, so an established account would have been deleted a day after changing its email. It also let anyone move an account onto an address they do not own, which is what that job exists to prevent. Don't reintroduce it without a separate "verified once" marker.
- Verification mail is **not sent inline**: `signUp` and `signIn` publish `VerificationEmailRequestedEvent` and `VerificationEmailListener` calls Resend after commit. `signUp` is `@Transactional` and used to hold a pooled connection for the whole third-party request. The listener logs failures rather than rethrowing — partly because there is nothing to roll back after commit, partly because `sendVerifyLink` throws `VERIFY_EMAIL_COOLDOWN` within 30s of the previous send and that used to fail the login of an unverified user who simply tried twice. Note this is still synchronous on the request thread; it is the seam a Kafka produce replaces.

### Real-time is netty-socketio, a SEPARATE server — not Spring WebSocket

- `socket/` runs a standalone Socket.IO server (netty-socketio) on **port 9092**, started by `SocketIOServerRunner` (`CommandLineRunner`) alongside the Spring web port. It is a distinct server, not a Spring MVC endpoint.
- Socket auth: client passes the JWT as a `?token=` handshake query param; the handshake's `AuthorizationListener` decodes it and `SocketIOService.onConnect` reuses the result rather than verifying again.
- **Messages are addressed to the participants' personal rooms (`user:{id}`), not to the conversation's room.** This is load-bearing, not a style choice. A personal room is joined once at connect and never changes; a conversation room has to be joined, so a conversation created *after* a client connected had nobody in its room and every message in it went to no one until that client reconnected — the first message to a new friend, the one that creates the conversation, could never arrive in real time at all. `SocketRelay.toConversation` is the only correct way to emit to a conversation. (`join-conversation` and the conversation rooms still exist and still check membership; nothing depends on them for delivery.)
  - The personal room is joined **first and unconditionally** in `subscribeUserToConversations`. It used to come after an early return for "no conversations yet", so a brand new account joined no room at all.
- **Nothing is pushed from inside a transaction.** A service publishes a domain event; a listener in `listener/` handles it with `@TransactionalEventListener(AFTER_COMMIT)` and does the emitting. `NewMessageEvent` → `new-message`, `ConversationCreatedEvent` → `new-group`, `ConversationReadEvent` → `read-message`. No service holds a `SocketIOServer` any more, and none should: emitting before commit told clients about writes that were not yet durable. Events carry mapped DTOs, never entities — the listener runs with no open session.
  - Publishing from a method with **no** transaction silently discards the event. That is why `createConversation` is `@Transactional`; `VerificationEmailListener` instead opts into `fallbackExecution = true` because `signIn` has no transaction.
- `presence-changed` carries one user id and a flag. The full `online-users` list is sent **only to the arriving socket** — it used to be rebroadcast to everyone on every connect and disconnect. `RedisPresenceStore.add`/`remove` report the presence *transition*, so a second tab is not an arrival and closing one of two tabs is not a departure.

### Running more than one instance

Both pieces that were per-process are now shared through Redis, with no extra dependency — `spring-boot-starter-data-redis` was already here, and netty-socketio's Redisson store factory was deliberately not used.

- **Fan-out: `SocketRelay`.** Every emission goes to this instance's clients *and* onto the `wtsend:socket:relay` pub/sub channel; each instance emits what it receives to its own clients, skipping envelopes it published itself (matched on `InstanceId`). Room membership is never shared — each instance already holds the right membership for its own clients. The cost of that choice: `getRoomOperations(...)` and `getAllClients()` stay node-local, so counting clients across the cluster is not supported. The envelope carries the payload as a `JsonNode`, so no Java class name travels on the wire; a subscriber that instantiated named classes would be a deserialisation gadget for anyone who can write to Redis. `SocketJson` holds the Jackson 2 settings **both** mappers must share — if only netty-socketio's registered `JavaTimeModule`, the same message would reach `createdAt` as an ISO string on one instance and an epoch array on another.
- **Presence: `RedisPresenceStore`.** `presence:sockets:{userId}` is a set of `"{instanceId}:{sessionId}"`, `presence:online` a ZSET of userId → last heartbeat. Add/remove run as Lua because the "did presence change" decision reads a value it just wrote. `PresenceHeartbeat` re-stamps this instance's users every 30s and a read ignores anything older than 90s, so an instance killed with `-9` needs nobody to notice it: its users age out on their own. That heartbeat is the one scheduled job **without** `@SchedulerLock` — each instance reports sockets only it knows about, so a lock would silence all but one and the rest's users would age out while still connected.

  Two things about that are easy to get wrong, and both were wrong here at first:

  - **The sweep has to announce what it removed.** `pruneStale` returns the user ids it dropped and `PresenceHeartbeat` sends `presence-changed(false)` for each. A page that is already open never re-reads the online list, so when an instance dies without running its disconnect handlers, that sweep is the *only* moment anyone can be told those users are gone — deleting the rows quietly left every open page showing a vanished user as online until it was reloaded. Read and remove happen in one Lua script, so with several instances only the one that actually removed the entries announces them.
  - **Arrival is decided by the online list, not by the socket count.** An instance killed with `-9` leaves socket members nobody will ever remove (that is what the `{instanceId}:` prefix marks them as). Counting those made a returning user look like someone opening a second tab, so no arrival was broadcast and every open page kept showing them offline. `add` now compares against a *fresh* entry in `presence:online` — the same thing readers look at — which makes the 90-second window the single authority for both directions.
- **`User.showOnlineStatus` is honoured at the write, not the read.** A hidden user's sockets are still counted (closing one of two tabs must not look like their last) but no entry is created in `presence:online`, so nobody sees them. `touch` uses `ZADD ... XX` precisely so it refreshes an existing entry and never recreates one — without that, hiding would reverse itself at the next heartbeat 30s later. `hide`/`show` work from any instance because the entry is keyed by user, not by socket; `PresenceVisibilityListener` applies them after the write and announces through the relay. A disconnect broadcasts `presence-changed(false)` **unconditionally**: the `UserResponse` cached on the socket dates from connect and may be stale, and telling clients an already-invisible user is offline costs nothing, while skipping it wrongly leaves someone shown online after they left.
- `SocketIOServerRunner.destroy()` **disconnects every client before `server.stop()`**. `stop()` closes the channels without running the disconnect handlers, which would leave this instance's users shown online for up to 90s.
- No sticky sessions needed: the client uses `transports: ["websocket"]`, so a session is one connection rather than a series of polling requests that must all land on the same instance.
- Verified by running two instances (`--server.port` / `--socketio.port` / `--app.instance-id`): message both ways, `new-group` plus a group message, `online-users` covering both nodes, arrival and departure, and `kill -9` on one instance dropping its users from the online list at ~90s before the survivor's heartbeat pruned the entry.

> Branch `impl-stomp` is named for a migration to STOMP that was **abandoned and removed**. A `configs/WebSocketConfiguration` + `configs/WebSocketHandshake` pair briefly existed (broker on `/ws`, plus a handshake interceptor that authenticated nobody) with zero `@MessageMapping` handlers; both files and the `spring-boot-starter-websocket` dependency are gone. netty-socketio is the only real-time mechanism — don't reintroduce STOMP alongside it.

## Config & secrets

Config comes entirely from `application.properties` + `application-local.properties`. The `java-dotenv` dependency reads nothing — **no Java code loads `.env`**. Docker Compose does read it, for `MYSQL_PASSWORD`, which therefore has to stay equal to `spring.datasource.password`.

`application-local.properties` holds real credentials (DB password, Cloudinary, Resend, Google/Facebook OAuth, RSA key paths) but is **gitignored and has never been committed** — `git log --all -- src/main/resources/application-local.properties` is empty, as it is for `.env`, `*.pem`, and `certs/`. The only tracked config is `application.properties`, which contains just the app name and active profile. Keep it that way: never move a credential into a tracked file, and don't echo their values.

CORS origins come from `app.cors.allowed-origin-patterns` (local profile only). It defaults to empty, so an unconfigured profile allows no cross-origin traffic — deliberate, since `allowCredentials(true)` plus a wildcard pattern is exploitable.

External services: **Cloudinary** (media upload, `CloudinaryService`), **Resend** + Spring Mail with Thymeleaf templates in `resources/templates/emails/` (verification & password-reset email).

## Two things about Spring Boot 4 that cost time

**Jackson 3.** Boot 4 autoconfigures `tools.jackson.databind.json.JsonMapper`; there is **no** `com.fasterxml.jackson.databind.ObjectMapper` bean. Jackson 2 is still on the classpath only because netty-socketio's `JacksonJsonSupport` needs it, and `SocketIOConfig` builds its own Jackson 2 mapper for that. Inject the Jackson 3 type in application code — asking for the Jackson 2 one fails context startup.

**Autoconfiguration is split per technology.** `spring-boot-jackson`, `spring-boot-flyway`, and so on. Adding the plain library (`flyway-core`) gives you the classes and none of the wiring, and the symptom is silence rather than an error.

## Kafka: one topic, for email

Scope is deliberately small: `wtsend.notify.email.v1` plus its `.dlt`, and nothing else. Verification and password-reset mail is produced there instead of being sent on the request thread. What the hop buys is not speed -- it is that a failed send leaves a record in the dead-letter topic that can be read and replayed, where before it was a log line and nothing else.

- **`KafkaEventPublisher` is the only place anything is written.** Adding a topic is an edit to one class, and the day an outbox is wanted, `publishEmail` becomes a row insert with nothing above it changing.
- **The event carries a user id and a kind. Nothing else.** No token (records live seven days; a live verification token in one is a credential at rest), no email address (a copy of a column the consumer is about to read, in a log that outlives the request), no rendered HTML (fixing a template typo would otherwise mean replaying the topic).
- **Kafka down must not take the API with it.** `admin.fail-fast=false`, and `max.block.ms=5000` instead of the default 60s, which would park a Tomcat thread per send until the pool was gone. Verified by running with the broker stopped: the app boots, `/api/auth/register` answers 201, and the lost event is logged at ERROR with its ids and counted in `wtsend.kafka.send.failures`. **There is deliberately no Kafka health indicator** -- wiring one into `/actuator/health` would let a broker outage fail a liveness probe and have the orchestrator kill healthy pods.
- `/actuator/**` currently requires a JWT, because `SecurityConfig` ends in `anyRequest().authenticated()` and nothing exempts it. Fine as a default; a deployment wanting an HTTP liveness probe has to open `/actuator/health` deliberately.

### Things here that cost real time to find

- **`delivery.timeout.ms` must be ≥ `linger.ms` + `request.timeout.ms`.** With the default 30s request timeout, `delivery.timeout.ms=30000` is *rejected* and the producer cannot be constructed at all -- every send then fails with a `ConfigException` and nothing is ever published. Hence `request.timeout.ms=10000`. The embedded-Kafka test now sets the same values so an invalid combination fails there rather than in production.
- **The dead-letter template cannot be the ordinary one.** A record that failed to deserialise reaches the recoverer as the raw `byte[]`, and a JSON serializer turns that into base64 -- the DLT held `"e3t7"` where the original bytes were `{{{`, so replaying a poison pill replayed base64. `KafkaProducerConfig` builds one template with a `DelegatingByTypeSerializer` (byte[] → raw, everything else → JSON) and that same template serves both paths. Declaring a `KafkaTemplate` bean also switches Boot's own off, which is why the producer's serializers are not set in properties.
- **`ErrorHandlingDeserializer` is not optional.** Without it a malformed record throws inside `Consumer.poll()`, before the listener or the error handler can see it: the offset never advances, the next poll throws on the same record, and that partition pins a core for ever.
- **Do not list `AppException` as non-retryable.** It covers both `USER_NOT_FOUND` (deterministic) and `EMAIL_SEND_FAILED` wrapping a 503 from Resend (exactly what retrying is for). `EmailNotificationConsumer` sorts by `ErrorCode` and wraps only the deterministic ones in `NonRetryableEventException`. One known roughness: a Resend 422 (invalid address) is permanent but arrives as `EMAIL_SEND_FAILED`, so it burns five attempts before the DLT.
- **Deduplication is the cooldown that was already there.** `VERIFY_EMAIL_COOLDOWN` from `EmailService` is treated as success by the consumer. No new state, and it deduplicates by intent ("do not spam this user") rather than by event id.
- Boot 4 moved the Kafka autoconfiguration to `org.springframework.boot.kafka.autoconfigure`, and spring-kafka 4.1 ships **two** sets of JSON serializers: `JsonSerializer`/`JsonDeserializer` (Jackson 2) and `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3). This app is on Jackson 3, so it uses the latter. Spring 7 also removed `ExponentialBackOffWithMaxRetries`; `ExponentialBackOff` carries `maxAttempts` itself now.
- The broker image is `apache/kafka:4.1.0` and needs **`CLUSTER_ID`**, not `KAFKA_CLUSTER_ID`. Host access is on **29092** only -- 9092 is the netty-socketio server -- and it advertises two listeners because the app runs on the host while kafka-ui runs inside the compose network. Advertise only `kafka:9092` and the app bootstraps fine, then hangs on the first send.
- `traceId` comes from `RequestTraceFilter` (honours an inbound `X-Request-Id`, echoes it back) and travels in the event envelope so a signup and the email it causes minutes later on a consumer thread share one id. It was null on every record until that filter existed.

## Scheduled work

`@EnableScheduling` is on. `jobs/UnverifiedAccountCleanupJob` deletes registrations left unverified for 24h, hourly per `app.cleanup.unverified-accounts.cron`. Every instance runs its own scheduler, so jobs carry `@SchedulerLock` (ShedLock, Redis-backed, no table — see `configs/SchedulerLockConfig`). The sweep is also written as a single conditional `DELETE`, so a double run removes nothing rather than racing.
