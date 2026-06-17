# CONCERNS
> Last updated: 2026-06-17 | Source: codebase scan

## Summary

WalletAssistant is a well-structured modular monolith with solid conventions. The major concerns centre around missing input validation at the HTTP boundary, unbounded read queries that will degrade under data growth, and the MCP layer bypassing authentication entirely. No TODO/FIXME comments exist, and modularity constraints are actively enforced by `ModularityTests`. Most risks are medium severity and correctable without architectural change.

---

## HIGH Priority Concerns

### 1. No `@Valid` on `@RequestBody` — batch size limit is declared but never enforced

**Files:** `src/main/java/org/dawid/cisowski/walletassistant/walletevents/WalletEventsController.java` (line 28), `src/main/resources/application.yml` (line 95 — `app.batch.max-events: 100`)

**Issue:** `@RequestBody SubmitEventsRequest request` has no `@Valid` annotation. `AppProperties` exposes `batch.max-events = 100`, but `WalletEventsController` and `WalletEventsService` never read or enforce it. A caller can submit an unbounded number of events in a single POST, each persisted in a transaction loop, causing OOM or long-running transactions.

**Impact:** Denial-of-service via large payloads; single transaction holds DB locks for all N events.

**Fix:** Add `@Valid` to the parameter, add `@Size(max = 100)` to the `List<EventEnvelope> events` field in `SubmitEventsRequest`, and inject `AppProperties` into the controller to validate the list size before delegating.

---

### 2. MCP tools bypass all authentication — hardcoded `DEFAULT_USER`

**File:** `src/main/java/org/dawid/cisowski/walletassistant/mcp/WalletTools.java` (lines 37, 157, 166)

**Issue:** All write operations (`recordExpense`, `recordAccountBalance`, `recordInvestmentSnapshot`) use `private static final String DEFAULT_USER = "default-user"` instead of a caller identity. Read operations accept a `userId` string parameter but fall back to `DEFAULT_USER` if blank. There is no authentication check in `WalletTools`; any MCP client that reaches the `/mcp` or `/sse` endpoints can write data as `default-user`.

**Impact:** If MCP is enabled (`MCP_ENABLED=true`), any Claude AI client (or attacker that discovers the endpoint) can create, read, and corrupt financial data without device authentication.

**Fix:** Propagate authenticated device ID into MCP context (e.g., via Spring Security `SecurityContextHolder` or a request-scoped bean set by the HMAC filter). Gate the `/sse` and `/mcp` paths more strictly when MCP is enabled.

---

### 3. Unbounded queries on `accounts` and `investments` history tables

**Files:**
- `src/main/java/org/dawid/cisowski/walletassistant/accounts/AccountsRepository.java` — `findByUserIdOrderByRecordedDateDesc` returns `List`
- `src/main/java/org/dawid/cisowski/walletassistant/investments/InvestmentsRepository.java` — `findByUserIdOrderByRecordedDateDesc` and `findByUserIdAndInvestmentTypeOrderByRecordedDateDesc` return `List`

**Issue:** Both repositories load the entire history for a user with no pagination or date range bound. A user with years of daily snapshots (365+ rows per account/investment per year) will trigger full table scans returned as a single in-memory list.

**Impact:** Increasing memory pressure and response latency with data growth. No index on `(user_id, recorded_date)` composite — each query uses separate single-column indexes.

**Fix:** Add composite indexes `(user_id, recorded_date DESC)` for both tables. Introduce `Pageable` parameters or hard date-range limits. Consider returning only the most recent N snapshots for the history endpoint.

---

### 4. HMAC timestamp tolerance is 600 seconds (10 minutes) by default

**File:** `src/main/resources/application.yml` (line 86 — `tolerance-seconds: ${HMAC_TOLERANCE_SEC:600}`)

**Issue:** The default replay-prevention window allows requests signed up to 10 minutes in the past or future. Combined with nonce protection (also 600s TTL), this is on the high end for HMAC-based APIs. If the nonce cache is lost (restart without durable cache), previously seen nonces are forgotten and stale signatures from within the window become valid.

**Impact:** Elevated replay attack surface during cache restarts. Standard practice is 30–300 seconds.

**Fix:** Lower the default to 300s (`HMAC_TOLERANCE_SEC:300`). Document the restart vulnerability and consider persisting nonces via the existing JDBC event store or Redis rather than in-memory Caffeine.

---

## MEDIUM Priority Concerns

### 5. No input validation on query parameters (`from`, `to`, `year`, `month`)

**Files:**
- `src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpensesController.java` (lines 21–25)
- `src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpensesService.java` (line 35)

**Issue:** `getExpenses` accepts arbitrary `from`/`to` date ranges with no maximum span validation. A caller can request 10 years of expense data in one call, loading all matching rows into memory. Similarly, `getMonthlySummary` accepts any `year`/`month` integer without range validation.

**Fix:** Add `@Validated` to controllers and validate that `to` minus `from` does not exceed a reasonable window (e.g., 366 days). Validate `month` is between 1 and 12.

---

### 6. Payload parsing in `Expense.createFromEvent` throws unchecked exceptions with no caller error handling

**File:** `src/main/java/org/dawid/cisowski/walletassistant/expenses/Expense.java` (lines 40–52)

**Issue:** `createFromEvent` throws `IllegalArgumentException` for missing or malformed JSONB payload fields (`amount`, `category`, `accountType`). The caller `ExpensesProjector.projectExpense` does not catch these; the uncaught exception propagates up through `ExpensesEventsListener.onExpensesStored`, which is an `@ApplicationModuleListener`. Spring Modulith will retry the event but if the payload is structurally corrupt, every retry will throw the same exception indefinitely until the event is marked as failed.

**Fix:** Wrap payload parsing in `ExpensesProjector` with a try/catch that logs the malformed event and skips it rather than retrying indefinitely. Same pattern applies to `AccountsProjector` and `InvestmentsProjector`.

---

### 7. `ApiKeyAuthenticationFilter` stores the raw API key in `AppProperties` memory

**File:** `src/main/java/org/dawid/cisowski/walletassistant/config/AppProperties.java` (lines 68–70)

**Issue:** `ApiKeyConfig` holds both the raw `key` string (from environment) and the computed `keyHash`. The raw key remains in the JVM heap for the application lifetime. A heap dump would expose the API key in plaintext.

**Fix:** Zero-out the `key` field after computing `keyHash` in `validate()`. Set `this.key = null` after `this.keyHash = sha256(key)`.

---

### 8. `WalletTools` MCP tools have no input validation or injection protection

**File:** `src/main/java/org/dawid/cisowski/walletassistant/mcp/WalletTools.java` (lines 47–72)

**Issue:** Tool parameters such as `description`, `merchant`, `category`, and `accountType` are passed directly to payload maps and stored as JSONB without sanitization. `category` and `accountType` are documented as enum values but there is no server-side validation before calling `storeEvents`. Invalid enum names cause `IllegalArgumentException` deep in `Expense.createFromEvent` (from `ExpenseCategory.fromString`), surfacing as an async projection failure rather than a clean API error.

**Fix:** Validate `category` and `accountType` against their respective enums before building the payload in `WalletTools`. Add length limits on free-text fields (`description`, `merchant`).

---

### 9. No integration test coverage for the MCP module or `McpSseForwardController`

**Observed test files:**
- `integration-tests/src/test/groovy/org/dawid/cisowski/walletassistant/AccountsSpec.groovy`
- `integration-tests/src/test/groovy/org/dawid/cisowski/walletassistant/ExpensesSpec.groovy`
- `integration-tests/src/test/groovy/org/dawid/cisowski/walletassistant/InvestmentsSpec.groovy`
- `integration-tests/src/test/groovy/org/dawid/cisowski/walletassistant/HmacAuthenticationSpec.groovy`

**Issue:** The `mcp` module (`WalletTools`, `McpSseForwardController`, `McpToolsConfiguration`) has zero integration test coverage. The SSE forwarding logic and tool invocation paths are untested.

**Fix:** Add an `McpSpec.groovy` integration test that enables MCP (`MCP_ENABLED=true`), calls `/sse`, and verifies tool execution and response structure.

---

### 10. `OptimisticLockingFailureException` retry in projectors retries only once

**File:** `src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpensesProjector.java` (lines 50–57)

**Issue:** `withOptimisticRetry` catches `ObjectOptimisticLockingFailureException` and retries exactly once. With parallel event consumption enabled (integration tests use `availableProcessors / 2`), concurrent projections of the same expense ID can fail the second attempt too. The same one-shot retry pattern exists in `AccountsProjector` and `InvestmentsProjector` (assumed, pattern is consistent).

**Fix:** Use a loop with a configurable max retry count (2–3), or use `@Retryable` from Spring Retry with exponential backoff. Log final failure as an error after exhausting retries.

---

### 11. No composite index on `expense_projections (user_id, occurred_date)`

**File:** `src/main/resources/db/migration/V2__create_expense_projections_table.sql`

**Issue:** `findByUserIdAndOccurredDateBetween` is the primary read query for expenses. The migration creates separate indexes on `user_id` and `occurred_date` but no composite `(user_id, occurred_date DESC)` index. PostgreSQL will use one index (likely `user_id`) and filter by date in memory.

**Fix:** Add migration `V5__add_composite_indexes.sql` with:
```sql
CREATE INDEX idx_expense_projections_user_date ON expense_projections (user_id, occurred_date DESC);
CREATE INDEX idx_account_snapshots_user_date ON account_balance_snapshots (user_id, recorded_date DESC);
CREATE INDEX idx_investment_snapshots_user_date ON investment_snapshots (user_id, recorded_date DESC);
```

---

## LOW Priority Concerns

### 12. `HMAC_DEVICES_JSON` loaded from env as raw JSON string

**File:** `src/main/java/org/dawid/cisowski/walletassistant/config/AppProperties.java` (lines 39–51)

**Issue:** Device secrets are passed as a base64-encoded JSON blob in an env var (`HMAC_DEVICES_JSON`). This is operationally awkward, error-prone to rotate, and not compatible with secret managers that inject individual secrets per key. There is no validation that the decoded base64 secret meets a minimum length.

**Fix:** Support individual env vars per device (`HMAC_DEVICE_{ID}_SECRET`) as an alternative. Add minimum secret length validation (e.g., 32 bytes after decoding).

---

### 13. `McpSseForwardController.baseUrl` trusts `X-Forwarded-Proto` and `X-Forwarded-Host` headers without validation

**File:** `src/main/java/org/dawid/cisowski/walletassistant/mcp/McpSseForwardController.java` (lines 50–57)

**Issue:** The forwarded URL is constructed from untrusted request headers without any validation or allowlist. A crafted `X-Forwarded-Host` header could redirect internal MCP calls to an attacker-controlled host (SSRF).

**Fix:** Validate that the constructed base URL matches a configured `MCP_BASE_URL` allowlist. Prefer using the configured `spring.ai.mcp.server.base-url` property directly instead of reconstructing from request headers.

---

### 14. `WalletEventsController` silently returns 401 when `deviceId` attribute is missing rather than logging

**File:** `src/main/java/org/dawid/cisowski/walletassistant/walletevents/WalletEventsController.java` (lines 32–35)

**Issue:** If neither filter sets the `deviceId` attribute (e.g., ApiKey is disabled and HMAC filter is misconfigured), the controller silently returns HTTP 401 with an empty body. This makes debugging auth failures harder since nothing is logged at the controller level.

**Fix:** Log a warning at the controller level when the attribute is missing, or rely on the HMAC filter's own 401 path which does log.

---

### 15. No `@Transactional` on `ExpensesProjector.deleteExpense` or `upsert`

**File:** `src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpensesProjector.java`

**Issue:** `deleteByExpenseId` and `save` are called from methods that are not annotated `@Transactional`. They rely on the transaction propagated from `ExpensesEventsListener`, which is managed by Spring Modulith's event infrastructure. If the event listener runs outside a transaction context (e.g., in a test or when `@ApplicationModuleListener` switches to async), the repository calls will use auto-commit mode.

**Fix:** Annotate `projectExpense` and `deleteExpense` in `ExpensesProjector` with `@Transactional` to make the transaction boundary explicit regardless of caller context.

---

## Gaps & Unknowns

- **EXPENSE_CORRECTED semantics:** The `Expense.createFromEvent` uses `resolveExpenseId` which reads `expenseId` from payload for corrections, but `WalletTools` has no `correctExpense` tool — it is unclear how corrections are triggered and whether the payload contract is documented anywhere.
- **Multi-user isolation:** All queries filter by `userId` (= `deviceId`), but there is no multi-tenancy test. It is untested whether device A can access device B's data through any code path.
- **Event store replay completeness:** `republish-outstanding-events-on-restart: true` is configured, but there are no tests verifying that projection state is consistent after a simulated crash-and-restart scenario.
- **Nonce cache durability:** The Caffeine nonce cache is in-memory only. Application restart clears it, making previously seen nonces within the tolerance window replayable. Severity depends on deployment restarts frequency.
- **`app.batch.max-events` is configured but unused** — see concern #1. It is unknown whether it was intended to be used in `WalletEventsService` or only as documentation.
