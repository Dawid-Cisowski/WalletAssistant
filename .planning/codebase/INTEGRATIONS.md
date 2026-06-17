# External Integrations
> Last updated: 2026-06-17 | Source: codebase scan

## Summary
WalletAssistant integrates with Claude AI clients via the MCP (Model Context Protocol) server exposed over HTTP, using Spring AI's MCP server implementation. The only external data store is PostgreSQL. Security integrations are entirely self-contained: HMAC signature verification and API key authentication are implemented in-process. Spring Modulith's JDBC event store handles durable internal event delivery without an external broker.

---

## APIs & External Services

### Claude AI (MCP Client)

WalletAssistant acts as an **MCP server** — it does not call Claude AI; Claude AI calls it.

- **Protocol:** Streamable MCP over HTTP (`spring.ai.mcp.server.protocol: STREAMABLE`)
- **SDK/Client:** `spring-ai-starter-mcp-server-webmvc` (Spring AI 2.0.0-M3)
- **Enabled via:** `MCP_ENABLED=true` environment variable (disabled by default)
- **MCP endpoint:** `/mcp` — primary MCP handler (registered by Spring AI auto-configuration)
- **SSE compatibility endpoint:** `/sse` (POST) — `McpSseForwardController` forwards to `/mcp`, supports `Mcp-Session-Id` header and SSE accept type (`text/event-stream`)
- **MCP tools registered:** `mcp/WalletTools.java` — tool methods covering record-expense, record-balance, record-investment, query-expenses, query-accounts, query-investments
- **Tool configuration:** `mcp/McpToolsConfiguration.java`
- **CORS for MCP:** `config/McpCorsConfig.java` — allows Claude AI origin for `/mcp` and `/sse`
- **Auth for MCP:** `/sse` and `/mcp` paths are in the `PROTECTED_PATHS` set — both HMAC and API key filters apply. Claude AI clients authenticate via the API key mechanism (`API_KEY_ENABLED=true`, `API_KEY_DEVICE_ID=claude-ai`)
- **Base URL config:** `MCP_BASE_URL` env var — used by `McpSseForwardController` to resolve the internal `/mcp` target

---

## Data Storage

### PostgreSQL

- **Version:** 16 (Docker: `postgres:16-alpine`)
- **Database name:** `wallet_assistant`
- **Connection:** `SPRING_DATASOURCE_URL` (default: `jdbc:postgresql://localhost:5432/wallet_assistant`)
- **Credentials:** `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (default: `wallet`/`wallet` for local dev)
- **Client/ORM:** Spring Data JPA + Hibernate (`PostgreSQLDialect`)
- **Connection pool:** HikariCP — max 10 connections, min 2 idle, 30s connection timeout
- **Batch settings:** `jdbc.batch_size=50`, `order_inserts=true`, `order_updates=true`
- **Timezone:** UTC enforced at JDBC level (`hibernate.jdbc.time_zone=UTC`)

**Tables managed by Flyway:**

| Table | Purpose | Migration |
|-------|---------|-----------|
| `wallet_events` | Immutable event log (write side), JSONB payload + idempotency key | `V1` |
| `expense_projections` | Denormalized expense read model | `V2` |
| `account_balance_snapshots` | Account balance read model | `V3` |
| `investment_snapshots` | Investment portfolio read model | `V4` |

**Spring Modulith event store tables** (auto-initialized by `spring-modulith-starter-jdbc`):
- Created on startup (`schema-initialization.enabled=true`)
- Stores outstanding domain events for durable at-least-once delivery
- Outstanding events republished on restart (`republish-outstanding-events-on-restart=true`)

---

## Event Publishing / Consuming (Spring Application Events)

No external message broker. All event flow uses Spring's `ApplicationEventPublisher` + Spring Modulith's `@ApplicationModuleListener`.

**Write side — `walletevents` module:**
- `WalletEventsService` stores events to `wallet_events` table, then publishes domain events after transaction commit
- Domain events published: `ExpensesStoredEvent`, `AccountSnapshotsStoredEvent`, `InvestmentSnapshotsStoredEvent`

**Read side — projection modules:**
- `expenses/ExpensesEventsListener.java` — `@ApplicationModuleListener` consuming `ExpensesStoredEvent`; delegates to `ExpensesProjector` to upsert `expense_projections`
- `accounts/` — listens to `AccountSnapshotsStoredEvent`, projects to `account_balance_snapshots`
- `investments/` — listens to `InvestmentSnapshotsStoredEvent`, projects to `investment_snapshots`

**Delivery guarantee:**
- Spring Modulith JDBC event store persists events before listeners run
- Failed/outstanding events are republished on next application startup
- Listener transactions are independent from the publishing transaction

**Modularity constraint:**
- `walletevents` must NOT import from `expenses`, `accounts`, or `investments` — enforced by `ModularityTests` (ArchUnit + Spring Modulith verification)

---

## Security Integrations

### HMAC Authentication Filter

- **Class:** `security/HmacAuthenticationFilter.java`
- **Order:** `Ordered.HIGHEST_PRECEDENCE + 2`
- **Protected paths:** `/v1/wallet-events`, `/v1/expenses`, `/v1/accounts`, `/v1/investments`, `/sse`, `/mcp`
- **Required headers:** `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`
- **Canonical string:** `method\npath+query\ntimestamp\nnonce\ndeviceId\nbody` (joined by `\n`)
- **HMAC algorithm:** `HmacSignature.calculate()` — implementation in `security/HmacSignature.java`
- **Timestamp tolerance:** configurable via `HMAC_TOLERANCE_SEC` (default: 600 seconds)
- **Device secrets:** loaded from JSON via `HMAC_DEVICES_JSON` env var; accessed through `DeviceSecretProvider` facade (`security/api/DeviceSecretProvider.java`), implemented by `security/DeviceSecretProviderAdapter.java`
- **Nonce replay protection:** Caffeine cache, TTL = `NONCE_CACHE_TTL_SEC` (default 600s), max 10,000 entries; accessed through `NonceCache` facade (`security/api/NonceCache.java`), implemented by `security/NonceCacheAdapter.java`
- **Body caching:** POST/PUT/PATCH requests wrapped in `CachedBodyHttpServletRequest` to allow body re-read after signature verification

### API Key Authentication Filter

- **Class:** `security/ApiKeyAuthenticationFilter.java`
- **Order:** `Ordered.HIGHEST_PRECEDENCE + 1` (runs before HMAC filter)
- **Enabled via:** `API_KEY_ENABLED=true`
- **Auth methods:** `Authorization: Bearer <key>` header OR `?apiKey=<key>` query param
- **Config:** `API_KEY`, `API_KEY_DEVICE_ID` (default: `claude-ai`), `API_KEY_READ_ONLY`
- **Effect:** If API key matches, sets `deviceId` attribute on request — HMAC filter skips the request if `deviceId` already set

### Spring Security

- **Class:** `config/SecurityConfig.java`
- **Role:** Wires filter chain; the two custom filters handle all authentication — Spring Security itself is configured to permit requests through to the custom filters

---

## HTTP Endpoints

| Path | Method | Module | Auth |
|------|--------|--------|------|
| `POST /v1/wallet-events` | Write events | `walletevents` | HMAC or API key |
| `GET /v1/expenses` | Query expenses by date/category | `expenses` | HMAC or API key |
| `GET /v1/accounts/*` | Query account balance history | `accounts` | HMAC or API key |
| `GET /v1/investments/*` | Query investment snapshots | `investments` | HMAC or API key |
| `POST /sse` | SSE/MCP compatibility forward | `mcp` | HMAC or API key |
| `POST /mcp` | MCP server endpoint (Spring AI) | `mcp` | HMAC or API key |
| `GET /actuator/health` | Health check | `config` | Open |
| `GET /actuator/info` | App info | `config` | Open |
| `GET /actuator/metrics` | Metrics | `config` | Open |
| `GET /actuator/prometheus` | Prometheus metrics | `config` | Open |
| `GET /swagger-ui.html` | OpenAPI docs UI | `config` | Open |

---

## Monitoring & Observability

**Metrics:**
- Micrometer Prometheus registry — `io.micrometer:micrometer-registry-prometheus`
- Scrape endpoint: `GET /actuator/prometheus`
- No external metrics push configured

**Health:**
- Spring Actuator health endpoint: `GET /actuator/health`
- Spring Modulith Actuator integration: module health included

**Error Tracking:**
- None detected — no Sentry, Datadog, or similar SDK present

**Logging:**
- Log4j2 async with LMAX Disruptor (Logback excluded via `configurations.all { exclude ... }`)
- JSON layout via `log4j-layout-template-json:2.23.1`
- Log levels: root=INFO, `org.dawid.cisowski.walletassistant`=DEBUG, Hibernate=WARN
- Log sanitization enforced: `SecurityUtils.sanitizeForLog()` strips `\r\n\t`; `maskDeviceId()` shows only first+last 4 chars of device IDs

---

## Gaps & Unknowns

- No CI/CD pipeline files detected — deployment target and environment secrets management are unknown.
- `MCP_BASE_URL` usage: `McpSseForwardController` derives base URL from request headers (`X-Forwarded-Proto`, `X-Forwarded-Host`) as a fallback — the `MCP_BASE_URL` config key appears in `application.yml` but the controller builds the URL dynamically. Exact precedence needs verification.
- No outgoing webhook or HTTP client calls to external APIs other than the internal MCP forward loop.
- No file storage integration (local filesystem only — not applicable for current feature set).
- No Redis, RabbitMQ, Kafka, or other external broker — all event delivery is in-process + JDBC.
