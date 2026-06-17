# WalletAssistant

A personal finance tracking backend built as a **modular monolith** with event sourcing. Records expenses, account balances, and investments, then projects them into queryable read models. Exposes all capabilities as **MCP tools** for Claude AI integration.

## Tech Stack

- **Java 21** (virtual threads enabled)
- **Spring Boot 4.1** · Spring Modulith 2.0 · Spring AI (MCP server)
- **PostgreSQL 16** · Flyway migrations
- **Gradle** (Kotlin DSL) · Spock + Testcontainers for testing

## Getting Started

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Run the application
./gradlew bootRun
```

The application starts on port `8080`. By default all environment variables have local defaults (see `application.yml`), so no additional configuration is needed to run locally.

## Configuration

All settings are driven by environment variables with sensible local defaults:

| Variable | Default | Description |
|---|---|---|
| `HMAC_DEVICES_JSON` | _(empty)_ | JSON map of deviceId → secret for HMAC auth |
| `HMAC_TOLERANCE_SEC` | `600` | Clock skew tolerance (seconds) |
| `API_KEY_ENABLED` | `false` | Enable API key authentication |
| `API_KEY` | `changeme-...` | Bearer token / query-param key |
| `API_KEY_READ_ONLY` | `false` | Restrict API key to read-only endpoints |
| `MCP_ENABLED` | `false` | Enable Spring AI MCP server |
| `MCP_BASE_URL` | _(empty)_ | Public base URL for MCP callbacks |
| `APP_TIMEZONE` | `Europe/Warsaw` | Timezone for date calculations |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/wallet_assistant` | Database URL |

## API

All endpoints are prefixed with `/v1` and require authentication.

### Authentication

Two mechanisms are supported, applied in order:

**API Key** — Bearer token or query parameter:
```
Authorization: Bearer <key>
# or
GET /v1/expenses?apiKey=<key>
```

**HMAC-SHA256** — Required headers:
```
X-Device-Id:  <deviceId>
X-Timestamp:  <unix epoch seconds>
X-Nonce:      <random UUID>
X-Signature:  <HMAC-SHA256(method + path + timestamp + nonce + deviceId + body)>
```

### Endpoints

```
POST /v1/wallet-events                    Submit one or more financial events (max 100)

GET  /v1/expenses?from=&to=&userId=       All expenses in date range
GET  /v1/expenses/{category}?from=&to=&userId=  Expenses filtered by category

GET  /v1/accounts/balances?userId=        Current balance per account type
GET  /v1/accounts/{accountType}/history   Balance history for an account

GET  /v1/investments/portfolio?userId=    Portfolio summary with gain/loss
```

### Event Payload Structure

Events are submitted as a JSON array. Each event carries a `type` and a `payload`:

```json
[
  {
    "type": "EXPENSE",
    "idempotencyKey": "uuid-v4",
    "occurredAt": "2026-06-17T10:00:00Z",
    "userId": "user-123",
    "payload": {
      "amount": 42.50,
      "currency": "PLN",
      "category": "FOOD_AND_DRINKS",
      "description": "Lunch"
    }
  }
]
```

**Expense categories:** `FOOD_AND_DRINKS` · `TRANSPORT` · `SHOPPING` · `ENTERTAINMENT` · `SUBSCRIPTIONS` · `HEALTH` · `HOUSING` · `UTILITIES` · `EDUCATION` · `TRAVEL` · `BUSINESS` · `OTHER`

**Account types:** `BUSINESS` · `PERSONAL_SAVINGS` · `PERSONAL_SPENDING`

**Investment types:** `IKE` · `XTB_STOCKS` · `XTB_ETF` · `SAVINGS_ACCOUNT` · `CRYPTO` · `OTHER`

## MCP Integration (Claude AI)

When `MCP_ENABLED=true`, the application serves as an MCP server at `/mcp` (SSE forwarded via `/sse`). Claude AI clients can connect and call the following tools:

| Tool | Description |
|---|---|
| `recordExpense` | Record a new expense event |
| `getExpenses` | Query expenses by date range |
| `getMonthlySummary` | Breakdown by category with totals |
| `recordAccountBalance` | Record an account balance snapshot |
| `getCurrentBalances` | Get all current account balances |
| `recordInvestmentSnapshot` | Record an investment value snapshot |
| `getPortfolioSummary` | Total portfolio value with gain/loss percentage |

## Architecture

### Event Sourcing + CQRS

All state changes are submitted as immutable events stored in the `wallet_events` table (JSONB payload). After commit, domain events are published and consumed by async listeners in projection modules that maintain denormalized read models.

```
POST /v1/wallet-events
  └─ WalletEventsService  →  wallet_events table
                          →  publishes: ExpensesStoredEvent
                                        AccountSnapshotsStoredEvent
                                        InvestmentSnapshotsStoredEvent

  └─ @ApplicationModuleListener (expenses)     →  expense_projections
  └─ @ApplicationModuleListener (accounts)     →  account_balance_snapshots
  └─ @ApplicationModuleListener (investments)  →  investment_snapshots
```

### Modules

| Module | Responsibility |
|---|---|
| `walletevents` | Write side — event ingestion, storage, publishing |
| `expenses` | Expense projection — query by date/category |
| `accounts` | Balance snapshot projection — account history |
| `investments` | Investment snapshot projection — portfolio summaries |
| `mcp` | MCP tool definitions for Claude AI |
| `security` | HMAC + API key filter chain |
| `config` | `AppProperties`, bean wiring |

`walletevents` is strictly isolated — it must not depend on any projection module. Cross-module communication is event-only. This is enforced by `ModularityTests`.

Each module exposes only its `api/` subpackage (facade interface + DTOs as Records). All other classes are package-private.

## Running Tests

```bash
# Unit tests (Spock specs)
./gradlew test

# Integration tests (requires Docker for Testcontainers)
./gradlew :integration-tests:test

# Single integration test class
./gradlew :integration-tests:test --tests "org.dawid.cisowski.walletassistant.SomeSpec"

# Coverage report (build/reports/jacoco/)
./gradlew jacocoTestReport

# Verify module boundaries
./gradlew test --tests "org.dawid.cisowski.walletassistant.ModularityTests"
```

Integration tests spin up a PostgreSQL container via Testcontainers and use rest-assured for HTTP assertions. Async projections are awaited with Awaitility.
