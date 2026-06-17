<!-- GSD:project-start source:PROJECT.md -->

## Project

**WalletAssistant**

WalletAssistant to osobisty system zarządzania finansami składający się z backendu Spring Boot (event sourcing, modular monolith) oraz aplikacji Android do przeglądania danych. Backend przyjmuje zdarzenia finansowe przez REST API i projektuje je w denormalizowane widoki do odczytu. Aplikacja Android umożliwia podgląd wydatków, kont i inwestycji z telefonu.

**Core Value:** Zawsze aktualny, zsynchronizowany obraz osobistych finansów — dostępny zarówno przez Claude AI (MCP) jak i przez aplikację Android.

### Constraints

- **Tech (backend)**: Java 21 + Spring Boot 4.1 — nie zmieniamy stacku backendu
- **Auth**: HMAC-SHA256 z nagłówkami `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature` — Android musi używać tego samego schematu co backend
- **Mobile**: Android only, Jetpack Compose — nie cross-platform
- **Repo**: Osobne repo dla Android — nie monorepo z backendem (zgodnie z wzorcem HealthAssistant)
- **Deploy**: Google Cloud Run — backend musi mieć Dockerfile i Cloud Build config
- **Personal use**: Brak SLA, brak rate limitingu, uproszczona obsługa błędów

<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->

## Technology Stack

## Summary

## Languages

- Java 21 — all production source code (`src/main/java/`)
- Groovy 5 — all test specifications (`src/test/groovy/`, `integration-tests/src/test/groovy/`)
- Kotlin — Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`, `integration-tests/build.gradle.kts`)

## Runtime

- Java 21 (configured via Gradle toolchain: `JavaLanguageVersion.of(21)`)
- Virtual threads enabled: `spring.threads.virtual.enabled=true`
- Gradle with Kotlin DSL
- Lockfile: not present (standard Gradle dependency resolution)

## Frameworks

- Spring Boot 4.1.0 (`org.springframework.boot` plugin)
- Spring Web MVC — REST controllers, servlet filters
- Spring Data JPA — repositories, entity lifecycle
- Spring Security — filter chain for HMAC and API key auth
- Spring Validation — `@Valid` bean validation
- Spring Actuator — health, info, metrics, prometheus endpoints
- Spring Cache — Caffeine-backed nonce cache
- Spring Modulith 2.0.1 (BOM)
- Spring AI 2.0.0-M3 (BOM)
- io.spring.dependency-management 1.1.7 — BOM imports for Spring AI, Modulith, Testcontainers

## Database

- PostgreSQL 16 (Docker image: `postgres:16-alpine`)
- JDBC driver: `org.postgresql:postgresql:42.7.4`
- Hibernate dialect: `PostgreSQLDialect`
- Connection pool: HikariCP (default Spring Boot, max-pool-size=10)
- Flyway (Spring Boot starter + `flyway-database-postgresql`)
- Migration scripts: `src/main/resources/db/migration/` (V1–V4)
- `ddl-auto: validate` — schema managed entirely by Flyway
- Spring Modulith JDBC event store — persists outstanding domain events, republishes on restart

## Testing Frameworks

- Spock 2.4 with Groovy 5.0 (`spock-core:2.4-groovy-5.0`)
- Testcontainers PostgreSQL + Spock extension
- ArchUnit 1.3.0 — modularity boundary enforcement
- Spring Modulith Test (`spring-modulith-starter-test`)
- JUnit Platform Launcher (runtime)
- JaCoCo 0.8.12 — coverage reports (XML + HTML)
- Separate Gradle subproject — runs against full Spring Boot context
- Spock Spring 2.4 (`spock-spring:2.4-groovy-5.0`)
- Testcontainers PostgreSQL (real DB, no mocks)
- REST Assured 6.0.0 + JSON Path — HTTP client for black-box API testing
- Awaitility 4.2.0 + Awaitility Groovy — async assertion helpers
- Parallel execution: `availableProcessors / 2`, 10-minute timeout, 1 GB heap

## Key Libraries

- Caffeine (via `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`)
- Used for nonce replay protection: `expireAfterWrite=600s, maximumSize=10000`
- Log4j2 async (`spring-boot-starter-log4j2`) — Logback excluded project-wide
- LMAX Disruptor 3.4.4 — async log queue
- `log4j-layout-template-json:2.23.1` — JSON log format
- Jackson with `jackson-datatype-jsr310` — Java time type support
- Lombok — `@Slf4j`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`
- MapStruct 1.5.5.Final — compile-time mapper generation (with `lombok-mapstruct-binding:0.2.0`)
- springdoc-openapi 3.0.2 (`springdoc-openapi-starter-webmvc-ui`) — Swagger UI at `/swagger-ui.html`
- Micrometer Prometheus registry — metrics scraped at `/actuator/prometheus`

## Configuration

- Profiles: `local` (default), `test`
- All secrets via environment variables (no defaults for production secrets):
- Config binding: `app.*` properties via `AppProperties.java` (`config/AppProperties.java`)
- `build.gradle.kts` — main application build
- `integration-tests/build.gradle.kts` — separate test project, references main project via `project(":")`
- `settings.gradle.kts` — multi-project settings

## Dev Tooling

- Docker Compose: `docker-compose.yml` — starts `wallet-assistant-postgres` on port 5432
- `./gradlew bootRun` — runs application locally with `local` profile
- Docker (for local Postgres and Testcontainers)
- Java 21 toolchain (auto-provisioned by Gradle if not present)

## Gaps & Unknowns

- No `.nvmrc`, `.python-version`, or Node.js tooling detected — Java-only project.
- No CI/CD pipeline files found (no `.github/`, `Jenkinsfile`, etc.) in the scanned tree.
- Gradle wrapper version not inspected (check `gradle/wrapper/gradle-wrapper.properties`).
- JaCoCo coverage thresholds not enforced — reports generated but no fail-on-coverage rule set.

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

## Summary

## Java 21 Features in Use

### Records — Pervasive Pattern

### Switch Expressions — Pattern Used in Service Layer

### var — Used Throughout

### Stream API — Mandatory, No Loops

### Optional — Used Instead of Null Returns

## DDD Patterns

### Rich Domain Model (No Anemic Objects)

### Factory Methods

### Facade Pattern for Module API

### Domain Events (Post-Commit)

## JPA Entity Conventions

### @Version — Optimistic Locking on Every Entity

### No @Setter — Protected No-Args Constructor

### Private Constructor + Static Factory

### Manual Getters (No Lombok @Getter)

## Validation Approach

### Eager Validation in Compact Constructors

### Validation at Payload Parsing Boundaries

## Error Handling

### Controller Layer — Safe Error Responses

### Domain Layer — Fail Fast

### Service Layer — Optional Chaining

### Optimistic Locking Retry

## Log Sanitization

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| JPA Entity classes | `[Domain]JpaEntity` | `ExpenseProjectionJpaEntity` |
| Repository interfaces | `[Domain]Repository` | `ExpensesRepository` |
| Service classes | `[Domain]Service` | `WalletEventsService` |
| Controller classes | `[Domain]Controller` | `WalletEventsController` |
| Facade interfaces | `[Domain]Facade` | `WalletEventsFacade` |
| Event listener classes | `[Domain]EventsListener` | `ExpensesEventsListener` |
| Projector classes | `[Domain]Projector` | `ExpensesProjector` |
| Domain events | `[Domain]StoredEvent` | `ExpensesStoredEvent` |
| Test specs | `[Subject]Spec` | `ExpenseSpec`, `HmacAuthenticationSpec` |

## Lombok Usage

- `@Slf4j` — logger field injection in services/filters
- `@RequiredArgsConstructor` — constructor injection for Spring beans
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — JPA protected constructor

## Gaps & Unknowns

- No `@Valid` / Bean Validation annotations found — it is unclear whether this is a deliberate architectural choice or an oversight for HTTP request bodies
- Error handling in projectors swallows optimistic lock failures after one retry with only a WARN log; no dead-letter queue or alerting observed
- `McpSseForwardController` uses raw HTTP client forwarding; error handling for downstream MCP failures is not evidenced in the scanned code

<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

## Summary

## System Overview

```text

```

## Module Responsibilities

| Module | Role | Key Files |
|--------|------|-----------|
| `walletevents` | Write side: ingest, store, and publish events | `WalletEventsService.java`, `WalletEventsController.java` |
| `expenses` | Project expense events; query by date/category | `ExpensesEventsListener.java`, `ExpensesProjector.java`, `ExpensesService.java` |
| `accounts` | Project balance snapshots; query account history | `AccountsEventsListener.java`, `AccountsProjector.java`, `AccountsService.java` |
| `investments` | Project investment snapshots; portfolio summaries | `InvestmentsEventsListener.java`, `InvestmentsProjector.java`, `InvestmentsService.java` |
| `mcp` | Expose all domain operations as Spring AI MCP tools to Claude AI | `WalletTools.java`, `McpSseForwardController.java` |
| `security` | HMAC + API key authentication filter chain | `HmacAuthenticationFilter.java`, `ApiKeyAuthenticationFilter.java` |
| `config` | `AppProperties` binding, `SecurityConfig`, `OpenApiConfig` | `AppProperties.java`, `SecurityConfig.java` |

## Data Flow

### Write Path (Event Ingestion)

### Event Fan-Out (Projection Path)

- `ExpensesStoredEvent` → consumed by `ExpensesEventsListener` (`@ApplicationModuleListener`)
- `AccountSnapshotsStoredEvent` → consumed by `AccountsEventsListener`
- `InvestmentSnapshotsStoredEvent` → consumed by `InvestmentsEventsListener`

### Read Path

## Domain Events (Cross-Module API)

| Event | Trigger |
|-------|---------|
| `ExpensesStoredEvent` | `EXPENSE_RECORDED`, `EXPENSE_CORRECTED`, or `EXPENSE_DELETED` events stored |
| `AccountSnapshotsStoredEvent` | `ACCOUNT_BALANCE_SNAPSHOT_RECORDED` stored |
| `InvestmentSnapshotsStoredEvent` | `INVESTMENT_SNAPSHOT_RECORDED` stored |

## Key Domain Concepts

- Expense: `EXPENSE_RECORDED`, `EXPENSE_CORRECTED`, `EXPENSE_DELETED`
- Account: `ACCOUNT_BALANCE_SNAPSHOT_RECORDED`
- Investment: `INVESTMENT_SNAPSHOT_RECORDED`

## Security Architecture

- Accepts Bearer token in `Authorization` header or `?apiKey=` query param
- Configurable: `API_KEY_ENABLED`, `API_KEY`, `API_KEY_DEVICE_ID`, `API_KEY_READ_ONLY`
- Sets `deviceId` request attribute on success, allowing HMAC filter to skip
- Required headers: `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`
- Skips if `deviceId` attribute already set (API key already authenticated)
- HMAC canonical string: `method\npath\ntimestamp\nnonce\ndeviceId\nbody`
- Nonce replay protection via Caffeine cache (600s TTL, 10k entries)
- Device secrets loaded from JSON config (`HMAC_DEVICES_JSON` env var)
- Timestamp tolerance window: 600s (configurable via `HMAC_TOLERANCE_SEC`)

## MCP Integration

## Architectural Constraints

- **Module isolation**: Only `api/` subpackages are public. Internal classes are package-private. Spring Modulith enforces this via `ModularityTests`.
- **Write → read isolation**: `walletevents` must never depend on projection modules. Domain events are the only cross-module coupling mechanism.
- **Event publication timing**: Events are published in `afterCommit()` hook — projection listeners always see a committed write-side state.
- **Idempotency**: Every ingested event carries an `idempotency_key`; duplicates are detected before persistence.
- **Virtual threads**: `spring.threads.virtual.enabled: true` — all request handling uses Project Loom virtual threads.
- **Global state**: `AppProperties` is a singleton Spring bean loaded from `application.yml` and env vars.

## Anti-Patterns

### Calling projection module services from walletevents

### Adding setters to JPA entities

## Error Handling

## Cross-Cutting Concerns

<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
