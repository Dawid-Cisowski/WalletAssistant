# Testing Patterns
> Last updated: 2026-06-17 | Source: codebase scan

## Summary

WalletAssistant uses two distinct test tiers: Spock unit specs for domain object behavior (co-located in main module), and a separate `integration-tests/` Gradle subproject for full end-to-end HTTP + database integration tests using Testcontainers, rest-assured, and Awaitility. All tests follow Spock's Given/When/Then structure. Integration tests are the primary coverage vehicle — they test through real HTTP, real PostgreSQL, and real async projection pipelines.

---

## Test Framework Stack

| Library | Purpose |
|---------|---------|
| Spock Framework (Groovy) | Test specification DSL for both unit and integration tests |
| Testcontainers `postgres:16-alpine` | Real PostgreSQL database for integration tests |
| rest-assured | HTTP client for integration test assertions |
| Awaitility | Polling assertion helper for async projection settlement |
| Spring Boot Test (`@SpringBootTest`) | Full application context for integration tests |
| JUnit (via Spock) | Test runner underneath Spock |

**Run commands:**
```bash
# Unit tests only
./gradlew test

# Integration tests (requires Docker — starts Testcontainers automatically)
./gradlew :integration-tests:test

# Single integration test class
./gradlew :integration-tests:test --tests "org.dawid.cisowski.walletassistant.ClassName"

# Single unit test
./gradlew test --tests "org.dawid.cisowski.walletassistant.SpecClassName"

# Coverage report
./gradlew jacocoTestReport
```

---

## Test File Organization

```
WalletAssistant/
├── src/test/groovy/                                         # Unit tests
│   └── org/dawid/cisowski/walletassistant/
│       ├── expenses/
│       │   ├── ExpenseSpec.groovy                           # Expense value object
│       │   └── ExpenseCategorySpec.groovy                   # Category enum
│       ├── security/
│       │   └── HmacSignatureSpec.groovy                     # HMAC calculation
│       └── walletevents/
│           └── WalletEventSpec.groovy                       # WalletEvent value object
│
└── integration-tests/src/test/groovy/                       # Integration tests (separate Gradle project)
    └── org/dawid/cisowski/walletassistant/
        ├── BaseIntegrationSpec.groovy                       # Shared base class
        ├── HmacAuthenticationSpec.groovy                    # Auth filter behavior
        ├── ExpensesSpec.groovy                              # Expense write + query flow
        ├── AccountsSpec.groovy                              # Account balance flow
        └── InvestmentsSpec.groovy                           # Investment snapshot flow
```

**Naming convention:** All test files end in `Spec.groovy`. No `Test.java` files exist.

---

## BaseIntegrationSpec

All integration tests extend `BaseIntegrationSpec` (`integration-tests/src/test/groovy/org/dawid/cisowski/walletassistant/BaseIntegrationSpec.groovy`).

**What it provides:**

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — full app context, real HTTP server
- `@ActiveProfiles("test")` — enables test configuration
- Shared `PostgreSQLContainer` (`postgres:16-alpine`, `withReuse(true)`) started once per JVM
- `JdbcTemplate jdbcTemplate` — direct DB access for setup/teardown
- `RestAssured.port` configured automatically

**Test device secrets (pre-configured in static initializer):**

| Device ID | Secret (Base64) | Decoded Secret |
|-----------|----------------|----------------|
| `test-device` | `dGVzdC1zZWNyZXQtMTIz` | `test-secret-123` |
| `test-expenses` | `dGVzdC1zZWNyZXQtMTIz` | `test-secret-123` |
| `test-accounts` | `dGVzdC1zZWNyZXQtMTIz` | `test-secret-123` |
| `test-investments` | `dGVzdC1zZWNyZXQtMTIz` | `test-secret-123` |
| `different-device-id` | `ZGlmZmVyZW50LXNlY3JldC0xMjM=` | `different-secret-123` |

Constants available in all specs via inheritance:
- `TEST_DEVICE_ID = "test-device"`
- `TEST_SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"`
- `DIFFERENT_DEVICE_ID = "different-device-id"`
- `DIFFERENT_SECRET_BASE64 = "ZGlmZmVyZW50LXNlY3JldC0xMjM="`

**Helper methods provided:**

```groovy
// Produces a RestAssured request spec with all HMAC headers signed
authenticatedPost(deviceId, secretBase64, path, body)
authenticatedGet(deviceId, secretBase64, path)

// Submits a wallet event, asserts 200, returns status string ("STORED" / "DUPLICATE")
submitEvent(deviceId, secretBase64, eventType, idempotencyKey, payload)

// Polls until the Closure returns true; max 5s, 200ms interval
awaitProjection(Closure<Boolean> condition)

// HMAC signing primitives
sign(method, path, timestamp, nonce, deviceId, body, secretBase64)
generateTimestamp()   // ISO-8601 Instant.now()
generateNonce()       // UUID.randomUUID().toString()
```

**Setup hook (runs before each test):**

```groovy
def setup() {
    RestAssured.port = port
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
    jdbcTemplate.update("DELETE FROM event_publication WHERE completion_date IS NULL")
}
```

---

## Unit Test Structure

Unit tests use plain `Specification` (no Spring context) and test domain object behavior in isolation.

```groovy
// Pattern from src/test/groovy/.../walletevents/WalletEventSpec.groovy
@Title("WalletEvent value object")
class WalletEventSpec extends Specification {

    // Shared constants at class level
    static final String EVENT_ID = "event-1"
    static final EventType EVENT_TYPE = EventType.EXPENSE_RECORDED

    def "should throw NullPointerException when required field '#fieldName' is null"() {
        when: "a WalletEvent is constructed with a null required field"
        new WalletEvent(eventId, ...)

        then: "construction fails with a descriptive NullPointerException"
        def exception = thrown(NullPointerException)
        exception.message == "${fieldName} must not be null"

        where:
        fieldName        | eventId  | ...
        "eventId"        | null     | ...
        "idempotencyKey" | EVENT_ID | ...
    }
}
```

**What unit tests cover:**
- All required-field null checks (data-driven `where:` table per field)
- Boundary value validation (`amount > 0`)
- Domain object state queries (`isActive()`)
- Factory method behavior (generated ID, timestamp, initial state)
- ID uniqueness across multiple creations

---

## Integration Test Structure

```groovy
// Pattern from integration-tests/src/test/groovy/.../ExpensesSpec.groovy
@Title("Feature: Expense Tracking")
class ExpensesSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-expenses"
    static final String SECRET = TEST_SECRET_BASE64

    // Per-test cleanup isolates data by user_id
    def setup() {
        jdbcTemplate.update("DELETE FROM expense_projections WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DEVICE_ID)
    }

    def "recording an expense stores it and makes it retrievable"() {
        given: "an EXPENSE_RECORDED event for a kebab"
        submitExpense(DEVICE_ID, SECRET, [amount: 26.00, category: "FOOD_AND_DRINKS"])

        and: "the projection has been applied"
        awaitProjection { getExpenses("2026-06-01", "2026-06-30").jsonPath().getList("").size() > 0 }

        when: "expenses for June 2026 are requested"
        def response = getExpenses("2026-06-01", "2026-06-30")

        then: "the expense is returned with the recorded amount and category"
        response.statusCode() == 200
        response.jsonPath().getDouble("[0].amount") == 26.0
    }
}
```

---

## Async Projection Assertion Pattern

Projections are applied asynchronously after transaction commit (Spring Modulith via `@ApplicationModuleListener`). Tests MUST poll until the read model is updated before asserting query results.

```groovy
// Submit the write event
submitExpense(DEVICE_ID, SECRET, [amount: 26.00, ...])

// Wait up to 5 seconds for projection to settle
awaitProjection { getExpenses("2026-06-01", "2026-06-30").jsonPath().getList("").size() > 0 }

// Now assert — projection is guaranteed to be visible
def response = getExpenses("2026-06-01", "2026-06-30")
response.statusCode() == 200
```

**Configuration:** 5 second timeout, 200ms poll interval (in `BaseIntegrationSpec.awaitProjection()`).

**Anti-pattern to avoid:** asserting query results immediately after `submitEvent()` without `awaitProjection()` — async listeners may not have run yet.

---

## Test Isolation Strategy

Each integration spec uses a dedicated `DEVICE_ID` constant (e.g., `"test-expenses"`, `"test-accounts"`) rather than the shared `TEST_DEVICE_ID`. Per-test `setup()` blocks delete rows `WHERE user_id = DEVICE_ID`, so parallel specs cannot interfere with each other.

```groovy
// Each spec class uses a unique device identity
static final String DEVICE_ID = "test-expenses"   // ExpensesSpec
static final String DEVICE_ID = "test-accounts"   // AccountsSpec
static final String DEVICE_ID = "test-investments" // InvestmentsSpec
```

**Parallel execution:** Configured at `availableProcessors / 2`.

---

## What Is Tested (Coverage Areas)

### Integration Tests (Primary Coverage)

| Spec | What is tested |
|------|----------------|
| `HmacAuthenticationSpec` | All HMAC failure modes: missing headers (X-Device-Id, X-Timestamp, X-Nonce, X-Signature), invalid signature, signature over wrong body, expired timestamp, future timestamp, unknown device, nonce replay, valid success path, public `/actuator/health` unauthenticated |
| `ExpensesSpec` | Write→project→read full flow, idempotency (DUPLICATE status), user data isolation, date range filtering, monthly summary category aggregation |
| `AccountsSpec` | Balance snapshot write→project→read, snapshot update (latest wins), multi-account-type isolation, user data isolation |
| `InvestmentsSpec` | Investment snapshot write→project→read (inferred from file existence) |

### Unit Tests (Domain Logic)

| Spec | What is tested |
|------|----------------|
| `WalletEventSpec` | Required field null checks (all 7 fields), `isActive()` state, factory method ID generation, uniqueness |
| `ExpenseSpec` | Required field null checks (amount, currency), positive amount validation, boundary value (0.01), valid construction |
| `ExpenseCategorySpec` | Category enum behavior (not read, assumed category validation) |
| `HmacSignatureSpec` | HMAC calculation correctness (not read, assumed) |

---

## Test Data Conventions

- **Dates:** Tests use concrete future dates (e.g., `2026-06-10T12:00:00Z`) rather than `Instant.now()` to avoid time-zone flakiness
- **Idempotency keys:** UUID-generated per test to avoid cross-test pollution (`UUID.randomUUID().toString()`)
- **Payload helpers:** Each spec defines a private `*Payload(Map overrides)` helper that merges test-specific fields over a defaults map
- **Currency:** Defaults to `PLN`
- **Timezone:** `Europe/Warsaw` used for `LocalDate` derivation from `Instant`

---

## Gaps & Unknowns

- `InvestmentsSpec.groovy` exists but was not fully read — coverage of investment edge cases (multi-snapshot, user isolation) is assumed to follow the same pattern as `AccountsSpec`
- `ExpenseCategorySpec.groovy` and `HmacSignatureSpec.groovy` were not fully read — content assumed to be domain behavior tests
- No E2E tests beyond HTTP layer — MCP tool behavior (`WalletTools.java`) has no dedicated test file observed
- No contract tests between modules — modularity is verified by `ModularityTests` (package dependency checks) but event payload shape contracts are not formally tested
- No performance or load tests observed
- Coverage thresholds in Jacoco configuration not verified — the `jacocoTestReport` task exists but enforcement limits are unknown
- `ModularityTests` class not read — assumed to use Spring Modulith's `ApplicationModules.verify()` to enforce module boundaries
