# Technology Stack
> Last updated: 2026-06-17 | Source: codebase scan

## Summary
WalletAssistant is a Java 21 modular monolith built on Spring Boot 4.1.0 with Spring Modulith for module isolation and CQRS-like event projection. It uses PostgreSQL 16 as the sole data store, Flyway for schema management, and Gradle with Kotlin DSL as the build system. Testing relies on Spock (Groovy) specs and Testcontainers for real-database integration tests.

---

## Languages

**Primary:**
- Java 21 — all production source code (`src/main/java/`)
- Groovy 5 — all test specifications (`src/test/groovy/`, `integration-tests/src/test/groovy/`)

**Build DSL:**
- Kotlin — Gradle build scripts (`build.gradle.kts`, `settings.gradle.kts`, `integration-tests/build.gradle.kts`)

---

## Runtime

**Environment:**
- Java 21 (configured via Gradle toolchain: `JavaLanguageVersion.of(21)`)
- Virtual threads enabled: `spring.threads.virtual.enabled=true`

**Package Manager / Build Tool:**
- Gradle with Kotlin DSL
- Lockfile: not present (standard Gradle dependency resolution)

---

## Frameworks

**Core:**
- Spring Boot 4.1.0 (`org.springframework.boot` plugin)
- Spring Web MVC — REST controllers, servlet filters
- Spring Data JPA — repositories, entity lifecycle
- Spring Security — filter chain for HMAC and API key auth
- Spring Validation — `@Valid` bean validation
- Spring Actuator — health, info, metrics, prometheus endpoints
- Spring Cache — Caffeine-backed nonce cache

**Modularity:**
- Spring Modulith 2.0.1 (BOM)
  - `spring-modulith-starter-core` — module isolation and verification
  - `spring-modulith-starter-jdbc` — JDBC-backed event publication log
  - `spring-modulith-events-jackson` — JSON serialization of domain events
  - `spring-modulith-actuator` — module health endpoint

**AI / MCP:**
- Spring AI 2.0.0-M3 (BOM)
  - `spring-ai-starter-mcp-server-webmvc` — exposes MCP tools over HTTP (Streamable protocol)

**Build/Dev:**
- io.spring.dependency-management 1.1.7 — BOM imports for Spring AI, Modulith, Testcontainers

---

## Database

**Database:**
- PostgreSQL 16 (Docker image: `postgres:16-alpine`)
- JDBC driver: `org.postgresql:postgresql:42.7.4`
- Hibernate dialect: `PostgreSQLDialect`
- Connection pool: HikariCP (default Spring Boot, max-pool-size=10)

**Migrations:**
- Flyway (Spring Boot starter + `flyway-database-postgresql`)
- Migration scripts: `src/main/resources/db/migration/` (V1–V4)
  - `V1__create_wallet_events_table.sql`
  - `V2__create_expense_projections_table.sql`
  - `V3__create_account_balance_snapshots_table.sql`
  - `V4__create_investment_snapshots_table.sql`
- `ddl-auto: validate` — schema managed entirely by Flyway

**Event Publication Log:**
- Spring Modulith JDBC event store — persists outstanding domain events, republishes on restart

---

## Testing Frameworks

**Unit Tests** (`src/test/`):
- Spock 2.4 with Groovy 5.0 (`spock-core:2.4-groovy-5.0`)
- Testcontainers PostgreSQL + Spock extension
- ArchUnit 1.3.0 — modularity boundary enforcement
- Spring Modulith Test (`spring-modulith-starter-test`)
- JUnit Platform Launcher (runtime)
- JaCoCo 0.8.12 — coverage reports (XML + HTML)

**Integration Tests** (`integration-tests/`):
- Separate Gradle subproject — runs against full Spring Boot context
- Spock Spring 2.4 (`spock-spring:2.4-groovy-5.0`)
- Testcontainers PostgreSQL (real DB, no mocks)
- REST Assured 6.0.0 + JSON Path — HTTP client for black-box API testing
- Awaitility 4.2.0 + Awaitility Groovy — async assertion helpers
- Parallel execution: `availableProcessors / 2`, 10-minute timeout, 1 GB heap

---

## Key Libraries

**Caching:**
- Caffeine (via `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`)
- Used for nonce replay protection: `expireAfterWrite=600s, maximumSize=10000`

**Logging:**
- Log4j2 async (`spring-boot-starter-log4j2`) — Logback excluded project-wide
- LMAX Disruptor 3.4.4 — async log queue
- `log4j-layout-template-json:2.23.1` — JSON log format

**Serialization:**
- Jackson with `jackson-datatype-jsr310` — Java time type support

**Code Generation:**
- Lombok — `@Slf4j`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`
- MapStruct 1.5.5.Final — compile-time mapper generation (with `lombok-mapstruct-binding:0.2.0`)

**API Documentation:**
- springdoc-openapi 3.0.2 (`springdoc-openapi-starter-webmvc-ui`) — Swagger UI at `/swagger-ui.html`

**Metrics:**
- Micrometer Prometheus registry — metrics scraped at `/actuator/prometheus`

---

## Configuration

**Environment:**
- Profiles: `local` (default), `test`
- All secrets via environment variables (no defaults for production secrets):
  - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
  - `HMAC_DEVICES_JSON` — JSON map of device IDs to HMAC secrets
  - `API_KEY` — Bearer token for Claude AI client
  - `MCP_ENABLED`, `MCP_BASE_URL`
  - `NONCE_CACHE_TTL_SEC`, `HMAC_TOLERANCE_SEC`
- Config binding: `app.*` properties via `AppProperties.java` (`config/AppProperties.java`)

**Build:**
- `build.gradle.kts` — main application build
- `integration-tests/build.gradle.kts` — separate test project, references main project via `project(":")`
- `settings.gradle.kts` — multi-project settings

---

## Dev Tooling

**Local Development:**
- Docker Compose: `docker-compose.yml` — starts `wallet-assistant-postgres` on port 5432
- `./gradlew bootRun` — runs application locally with `local` profile

**Key Gradle Tasks:**
```bash
./gradlew build -x test                    # Build, skip tests
./gradlew test                             # Unit tests + JaCoCo report
./gradlew :integration-tests:test          # Integration tests (requires Postgres)
./gradlew jacocoTestReport                 # Coverage report
./gradlew test --tests "...ModularityTests" # Modulith boundary verification
```

**Platform Requirements:**
- Docker (for local Postgres and Testcontainers)
- Java 21 toolchain (auto-provisioned by Gradle if not present)

---

## Gaps & Unknowns

- No `.nvmrc`, `.python-version`, or Node.js tooling detected — Java-only project.
- No CI/CD pipeline files found (no `.github/`, `Jenkinsfile`, etc.) in the scanned tree.
- Gradle wrapper version not inspected (check `gradle/wrapper/gradle-wrapper.properties`).
- JaCoCo coverage thresholds not enforced — reports generated but no fail-on-coverage rule set.
