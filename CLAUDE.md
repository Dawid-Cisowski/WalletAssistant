# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Start the database
docker-compose up -d

# Build (skips tests)
./gradlew build -x test

# Run unit tests only
./gradlew test

# Run integration tests (requires running PostgreSQL via docker-compose)
./gradlew :integration-tests:test

# Run a single integration test class
./gradlew :integration-tests:test --tests "org.dawid.cisowski.walletassistant.ClassName"

# Run a single unit test
./gradlew test --tests "org.dawid.cisowski.walletassistant.SpecClassName"

# Test coverage report
./gradlew jacocoTestReport

# Run application locally
./gradlew bootRun

# Run modularity verification
./gradlew test --tests "org.dawid.cisowski.walletassistant.ModularityTests"
```

## Architecture Overview

WalletAssistant is a **modular monolith** using Spring Modulith with CQRS-like separation. The system uses **event sourcing** — all financial state changes are stored as immutable events in `wallet_events`, and async listeners project them into read models.

### Data Flow

```
POST /v1/wallet-events
  → HMAC/API-key auth filters
  → WalletEventsService (stores to wallet_events, publishes domain events)
  → @ApplicationModuleListener in projection modules (expenses, accounts, investments)
  → Denormalized read tables (expense_projections, account_balance_snapshots, investment_snapshots)

GET /v1/expenses, /v1/accounts/*, /v1/investments/*
  → Query projection tables directly
```

### Module Structure

Seven Spring Modulith modules, each under `org.dawid.cisowski.walletassistant.<module>`:

| Module | Role |
|---|---|
| `walletevents` | Event ingestion, storage, publishing — the write side |
| `expenses` | Projects expense events; query by date/category |
| `accounts` | Projects balance snapshots; query account history |
| `investments` | Projects investment snapshots; portfolio summaries |
| `mcp` | MCP tools exposing all domain operations to Claude AI |
| `security` | HMAC + API key authentication filter chain |
| `config` | `AppProperties` binding, bean configuration |

**Critical modularity rule** (enforced by `ModularityTests`): `walletevents` must not depend on any projection module. Cross-module communication uses domain events only.

### Module Layout Convention

Each module has a public `api/` subpackage; all other classes are package-private:

```
module/
  api/                    # PUBLIC: facade interface + DTOs (Records)
  ModuleEntity.java       # package-private
  ModuleService.java      # package-private
  ModuleRepository.java   # package-private
  ModuleController.java   # package-private
```

### Security

Two ordered filters authenticate every request:

1. `ApiKeyAuthenticationFilter` (`HIGHEST_PRECEDENCE + 1`) — Bearer token or `?apiKey=` query param
2. `HmacAuthenticationFilter` (`HIGHEST_PRECEDENCE + 2`) — requires headers: `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`

HMAC canonical string: `method + path + timestamp + nonce + deviceId + body`. Nonce replay protection via Caffeine cache (600s TTL, 10k entries). Device secrets loaded from JSON config.

### MCP Integration

`mcp/WalletTools.java` exposes Spring AI MCP tools to Claude AI clients. The tools cover recording expenses/balances/investments and querying projections. SSE endpoint at `/sse` forwards to `/mcp`.

### Testing Conventions

- **Unit tests**: Spock specs (Groovy) in `src/test/groovy/`, `Given/When/Then` blocks
- **Integration tests**: Separate Gradle project at `integration-tests/`, extend `BaseIntegrationSpec`
  - Testcontainers PostgreSQL, rest-assured HTTP client, Awaitility for async assertions
  - Test device secrets: `test-device`, `different-device-id`
  - Parallel execution at `availableProcessors / 2`
- No mocking of internal components — only WireMock for external services

### Database Migrations

Flyway scripts in `src/main/resources/db/migration/`. Tables: `wallet_events` (JSONB payload, idempotency key), `expense_projections`, `account_balance_snapshots`, `investment_snapshots`.

### Key Domain Concepts

- **Event types** flow through `walletevents` and fan out as `ExpensesStoredEvent`, `AccountSnapshotsStoredEvent`, `InvestmentSnapshotsStoredEvent` after transaction commit
- **Expense categories**: `FOOD_AND_DRINKS`, `TRANSPORT`, `SHOPPING`, `ENTERTAINMENT`, `SUBSCRIPTIONS`, `HEALTH`, `HOUSING`, `UTILITIES`, `EDUCATION`, `TRAVEL`, `BUSINESS`, `OTHER`
- **Account types**: `BUSINESS`, `PERSONAL_SAVINGS`, `PERSONAL_SPENDING`
- **Investment types**: `IKE`, `XTB_STOCKS`, `XTB_ETF`, `SAVINGS_ACCOUNT`, `CRYPTO`, `OTHER`
- Timezone: `Europe/Warsaw` used for `LocalDate`/`Instant` conversions
