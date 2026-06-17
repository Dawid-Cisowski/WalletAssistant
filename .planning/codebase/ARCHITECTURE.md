# Architecture

> Last updated: 2026-06-17 | Source: codebase scan

## Summary

WalletAssistant is a modular monolith built with Spring Modulith, following an event-sourcing approach combined with CQRS-like separation. All financial state changes are stored as immutable events in `wallet_events`, and async module listeners project them into denormalized read tables. Cross-module communication is exclusively via published domain events — never direct service calls between modules.

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                    HTTP Clients / Claude AI (MCP)                        │
└────────────┬──────────────────────────────────────┬─────────────────────┘
             │ POST /v1/wallet-events                │ GET /v1/expenses
             │                                       │ GET /v1/accounts/*
             ▼                                       │ GET /v1/investments/*
┌────────────────────────┐                           │ /sse, /mcp
│  security module       │ ←── auth filters          │
│  ApiKeyAuthFilter      │                           │
│  HmacAuthFilter        │                           │
└────────────┬───────────┘                           │
             ▼                                       ▼
┌────────────────────────┐     ┌─────────────────────────────────────────┐
│   walletevents module  │     │   projection modules                    │
│   WalletEventsService  │     │   expenses / accounts / investments     │
│   (write side)         │     │   (read side — query projections only)  │
└────────────┬───────────┘     └───────────────┬─────────────────────────┘
             │                                 ▲
             │ afterCommit                     │ @ApplicationModuleListener
             │ ApplicationEventPublisher       │
             ▼                                 │
┌────────────────────────────────────────────────────────────────────────┐
│  Spring Modulith event bus                                              │
│  ExpensesStoredEvent / AccountSnapshotsStoredEvent /                   │
│  InvestmentSnapshotsStoredEvent                                        │
│  (published in walletevents.api — consumed by projection modules)      │
└────────────────────────────────────────────────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────────────────┐
│  PostgreSQL                                                             │
│  wallet_events (append-only, JSONB payload)                            │
│  expense_projections                                                   │
│  account_balance_snapshots                                             │
│  investment_snapshots                                                  │
└────────────────────────────────────────────────────────────────────────┘
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

1. `POST /v1/wallet-events` hits `WalletEventsController` (`walletevents/WalletEventsController.java`)
2. Auth filters validate HMAC or API key before the controller is reached
3. `WalletEventsService.storeEvents()` is called within a `@Transactional` boundary
4. Each event envelope is checked for idempotency via `idempotency_key` (`WalletEventsRepository`)
5. New events are persisted as `WalletEventJpaEntity` in `wallet_events` table
6. After transaction commit (`TransactionSynchronizationManager.afterCommit()`), stored events are grouped by type and published via `ApplicationEventPublisher`

### Event Fan-Out (Projection Path)

After commit, three domain events may be published (defined in `walletevents/api/`):

- `ExpensesStoredEvent` → consumed by `ExpensesEventsListener` (`@ApplicationModuleListener`)
- `AccountSnapshotsStoredEvent` → consumed by `AccountsEventsListener`
- `InvestmentSnapshotsStoredEvent` → consumed by `InvestmentsEventsListener`

Each listener delegates to a Projector that upserts into the denormalized read table.

Spring Modulith persists outstanding events to a JDBC event journal and republishes on restart (`republish-outstanding-events-on-restart: true`).

### Read Path

`GET` endpoints on each projection module query their own read table directly — no coordination with `walletevents` at read time.

## Domain Events (Cross-Module API)

All domain events are defined in `walletevents/api/` (the public surface of the write module):

| Event | Trigger |
|-------|---------|
| `ExpensesStoredEvent` | `EXPENSE_RECORDED`, `EXPENSE_CORRECTED`, or `EXPENSE_DELETED` events stored |
| `AccountSnapshotsStoredEvent` | `ACCOUNT_BALANCE_SNAPSHOT_RECORDED` stored |
| `InvestmentSnapshotsStoredEvent` | `INVESTMENT_SNAPSHOT_RECORDED` stored |

Event types are defined in `walletevents/api/EventType.java`. Payload data travels via `StoredEventData` records.

**Critical constraint** (enforced by `ModularityTests`): `walletevents` module must NOT import from any projection module package. This is a one-way dependency boundary.

## Key Domain Concepts

**Event Types** (`walletevents/api/EventType.java`):
- Expense: `EXPENSE_RECORDED`, `EXPENSE_CORRECTED`, `EXPENSE_DELETED`
- Account: `ACCOUNT_BALANCE_SNAPSHOT_RECORDED`
- Investment: `INVESTMENT_SNAPSHOT_RECORDED`

**Expense Categories** (`expenses/ExpenseCategory.java`): `FOOD_AND_DRINKS`, `TRANSPORT`, `SHOPPING`, `ENTERTAINMENT`, `SUBSCRIPTIONS`, `HEALTH`, `HOUSING`, `UTILITIES`, `EDUCATION`, `TRAVEL`, `BUSINESS`, `OTHER`

**Account Types** (`accounts/AccountType.java`): `BUSINESS`, `PERSONAL_SAVINGS`, `PERSONAL_SPENDING`

**Investment Types** (`investments/InvestmentType.java`): `IKE`, `XTB_STOCKS`, `XTB_ETF`, `SAVINGS_ACCOUNT`, `CRYPTO`, `OTHER`

**Timezone**: `Europe/Warsaw` for `LocalDate`/`Instant` conversions (configurable via `APP_TIMEZONE` env var)

## Security Architecture

Two servlet filters authenticate every protected request path (`/v1/wallet-events`, `/v1/expenses`, `/v1/accounts`, `/v1/investments`, `/sse`, `/mcp`):

**Filter 1 — `ApiKeyAuthenticationFilter`** (`HIGHEST_PRECEDENCE + 1`):
- Accepts Bearer token in `Authorization` header or `?apiKey=` query param
- Configurable: `API_KEY_ENABLED`, `API_KEY`, `API_KEY_DEVICE_ID`, `API_KEY_READ_ONLY`
- Sets `deviceId` request attribute on success, allowing HMAC filter to skip

**Filter 2 — `HmacAuthenticationFilter`** (`HIGHEST_PRECEDENCE + 2`):
- Required headers: `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`
- Skips if `deviceId` attribute already set (API key already authenticated)
- HMAC canonical string: `method\npath\ntimestamp\nnonce\ndeviceId\nbody`
- Nonce replay protection via Caffeine cache (600s TTL, 10k entries)
- Device secrets loaded from JSON config (`HMAC_DEVICES_JSON` env var)
- Timestamp tolerance window: 600s (configurable via `HMAC_TOLERANCE_SEC`)

Security public API in `security/api/`: `DeviceSecretProvider`, `NonceCache` interfaces. Implementations (`DeviceSecretProviderAdapter`, `NonceCacheAdapter`) are package-private.

## MCP Integration

`mcp/WalletTools.java` exposes Spring AI MCP tools to Claude AI clients. Tools cover recording and querying all domain types. SSE endpoint at `/sse` is forwarded to `/mcp` by `McpSseForwardController`. MCP can be disabled via `MCP_ENABLED=false`.

## Architectural Constraints

- **Module isolation**: Only `api/` subpackages are public. Internal classes are package-private. Spring Modulith enforces this via `ModularityTests`.
- **Write → read isolation**: `walletevents` must never depend on projection modules. Domain events are the only cross-module coupling mechanism.
- **Event publication timing**: Events are published in `afterCommit()` hook — projection listeners always see a committed write-side state.
- **Idempotency**: Every ingested event carries an `idempotency_key`; duplicates are detected before persistence.
- **Virtual threads**: `spring.threads.virtual.enabled: true` — all request handling uses Project Loom virtual threads.
- **Global state**: `AppProperties` is a singleton Spring bean loaded from `application.yml` and env vars.

## Anti-Patterns

### Calling projection module services from walletevents

**What happens:** Importing any class from `expenses`, `accounts`, or `investments` packages inside `walletevents`.
**Why it's wrong:** Violates Spring Modulith module boundaries enforced by `ModularityTests`; creates tight coupling that defeats the event-sourcing fan-out design.
**Do this instead:** Define data in `walletevents/api/StoredEventData.java` and publish a typed domain event from `walletevents/api/`. Projection modules consume the event via `@ApplicationModuleListener`.

### Adding setters to JPA entities

**What happens:** Using `@Setter` on `WalletEventJpaEntity`, `ExpenseProjectionJpaEntity`, `BalanceSnapshotJpaEntity`, or `InvestmentSnapshotJpaEntity`.
**Why it's wrong:** Violates the rich domain model convention; entities should expose business methods only.
**Do this instead:** Add a named business method (e.g., `markDeleted()`, `updateProjection()`) that encapsulates the state change.

## Error Handling

**Authentication errors:** `HmacAuthenticationFilter` writes a JSON `ErrorResponse` directly to the servlet response with HTTP 401. No exception propagates.

**Event processing:** Invalid event types return `EventStatus.INVALID` in `StoreEventsResult` without throwing; duplicates return `EventStatus.DUPLICATE`. Both are non-error outcomes at the HTTP level.

## Cross-Cutting Concerns

**Logging:** Slf4j (`@Slf4j` Lombok). User input sanitized before logging via `SecurityUtils.sanitizeForLog()`. Device IDs masked.
**Validation:** `@Valid` on controller parameters. Fail-fast at boundaries.
**Transactions:** `@Transactional` on `WalletEventsService.storeEvents()`. Projection listeners run in their own transactions managed by Spring Modulith.

---

*Architecture analysis: 2026-06-17*
