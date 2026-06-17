# Coding Conventions
> Last updated: 2026-06-17 | Source: codebase scan

## Summary

WalletAssistant follows strict Java 21 conventions enforced by project-level rules: Records for all value objects and DTOs, no imperative loops (Stream API only), rich domain models (no @Setter), and mandatory @Version on JPA entities. The codebase applies DDD patterns through a modular monolith structure using Spring Modulith, with module boundaries enforced by modularity tests. Security-sensitive logging is always sanitized via `SecurityUtils`.

---

## Java 21 Features in Use

### Records — Pervasive Pattern

Records are used for ALL value objects, domain objects, and DTOs. Compact constructors perform eager validation.

```java
// Domain object as record with validation — src/main/java/.../walletevents/WalletEvent.java
record WalletEvent(
        String eventId,
        String idempotencyKey,
        EventType eventType,
        Instant occurredAt,
        Map<String, Object> payload,
        String userId,
        Instant createdAt,
        String deletedByEventId,
        String supersededByEventId
) {
    WalletEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        // ... all required fields validated eagerly
    }
}
```

```java
// Value object as record — src/main/java/.../expenses/Expense.java
record Expense(String expenseId, String eventId, String userId, BigDecimal amount, ...) {
    Expense {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }
}
```

Private records also used for intermediate computation inside services:

```java
// src/main/java/.../walletevents/WalletEventsService.java
private record EnvelopeOutcome(EventResult result, Optional<StoredEventData> storedEvent) {
    static EnvelopeOutcome stored(...) { ... }
    static EnvelopeOutcome duplicate(...) { ... }
    static EnvelopeOutcome invalid(...) { ... }
}
```

### Switch Expressions — Pattern Used in Service Layer

```java
// src/main/java/.../walletevents/WalletEventsService.java
private boolean isExpenseType(EventType eventType) {
    return switch (eventType) {
        case EXPENSE_RECORDED, EXPENSE_CORRECTED, EXPENSE_DELETED -> true;
        default -> false;
    };
}
```

### var — Used Throughout

`var` is used for all local variable declarations where type is inferable.

### Stream API — Mandatory, No Loops

All collection processing uses Stream API. No `for`/`while`/`do-while` loops exist in the codebase.

```java
// src/main/java/.../walletevents/WalletEventsService.java
var outcomes = command.events().stream()
        .map(envelope -> processEnvelope(envelope, command.userId(), index.getAndIncrement()))
        .toList();

var expenses = grouped.entrySet().stream()
        .filter(entry -> isExpenseType(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .toList();
```

### Optional — Used Instead of Null Returns

```java
// src/main/java/.../expenses/Expense.java
private static String resolveExpenseId(Map<String, Object> payload) {
    return Optional.ofNullable(payload.get("expenseId"))
            .map(Object::toString)
            .orElseGet(() -> UUID.randomUUID().toString());
}
```

---

## DDD Patterns

### Rich Domain Model (No Anemic Objects)

Domain objects contain behavior, not just data. JPA entities expose business methods, not setters.

```java
// src/main/java/.../expenses/ExpenseProjectionJpaEntity.java
void applyCorrection(Expense expense) {
    this.eventId = expense.eventId();
    this.amount = expense.amount();
    // ... named business operation, not a generic setter
}
```

```java
// src/main/java/.../walletevents/WalletEvent.java
boolean isActive() {
    return deletedByEventId == null && supersededByEventId == null;
}
```

### Factory Methods

Domain objects created via static factory methods, not public constructors:

```java
// src/main/java/.../walletevents/WalletEvent.java
static WalletEvent create(String idempotencyKey, EventType eventType, Instant occurredAt,
                          Map<String, Object> payload, String userId) {
    return new WalletEvent(UUID.randomUUID().toString(), idempotencyKey, eventType,
                           occurredAt, payload, userId, Instant.now(), null, null);
}
```

### Facade Pattern for Module API

Every module exposes only its `api/` subpackage. Cross-module calls go through facade interfaces only.

```
walletevents/
  api/
    WalletEventsFacade.java   ← PUBLIC interface (cross-module boundary)
    ExpensesStoredEvent.java  ← PUBLIC domain event record
    StoredEventData.java      ← PUBLIC DTO record
  WalletEventsService.java    ← package-private implementation
  WalletEventsRepository.java ← package-private
```

### Domain Events (Post-Commit)

Domain events are published after transaction commit via `TransactionSynchronizationManager`, preventing projection listeners from observing uncommitted state.

```java
// src/main/java/.../walletevents/WalletEventsService.java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        publishGrouped(storedEvents);
    }
});
```

---

## JPA Entity Conventions

All JPA entities follow these mandatory rules:

### @Version — Optimistic Locking on Every Entity

```java
// src/main/java/.../expenses/ExpenseProjectionJpaEntity.java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

Applied in: `ExpenseProjectionJpaEntity`, `BalanceSnapshotJpaEntity`, `WalletEventJpaEntity`, `InvestmentSnapshotJpaEntity`.

### No @Setter — Protected No-Args Constructor

```java
// src/main/java/.../expenses/ExpenseProjectionJpaEntity.java
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExpenseProjectionJpaEntity {
    // No @Setter — fields mutated only via business methods like applyCorrection()
}
```

### Private Constructor + Static Factory

JPA entities use a private all-args constructor and expose a static `from(DomainObject)` factory:

```java
static ExpenseProjectionJpaEntity from(Expense expense) {
    return new ExpenseProjectionJpaEntity(expense.expenseId(), expense.eventId(), ...);
}
```

### Manual Getters (No Lombok @Getter)

Entities provide hand-written getters without Lombok `@Getter` to maintain encapsulation control.

---

## Validation Approach

### Eager Validation in Compact Constructors

All validation happens at object construction time. `Objects.requireNonNull()` with descriptive messages and `IllegalArgumentException` for business rule violations:

```java
// Pattern from Expense.java and WalletEvent.java
Objects.requireNonNull(amount, "amount must not be null");
if (amount.compareTo(BigDecimal.ZERO) <= 0) {
    throw new IllegalArgumentException("amount must be greater than zero");
}
```

### Validation at Payload Parsing Boundaries

Payload maps are parsed with explicit `Optional`-based accessors that throw `IllegalArgumentException` for missing required fields:

```java
// src/main/java/.../expenses/Expense.java
private static String stringValue(Map<String, Object> payload, String key) {
    return Optional.ofNullable(payload.get(key))
            .map(Object::toString)
            .orElseThrow(() -> new IllegalArgumentException("Missing required field: " + key));
}
```

Note: Spring's `@Valid`/`@NotNull` Bean Validation annotations are **not used** in this codebase. Validation is domain-layer driven via compact constructor guards.

---

## Error Handling

### Controller Layer — Safe Error Responses

Controllers return `ResponseEntity` and never expose internal exception details. Auth failures return `401` with only a structured error code (`HMAC_AUTH_FAILED`), not stack traces or internal messages.

```java
// src/main/java/.../walletevents/WalletEventsController.java
return Optional.ofNullable((String) httpRequest.getAttribute("deviceId"))
        .map(userId -> store(userId, request))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
```

### Domain Layer — Fail Fast

Constructors throw immediately on invalid input. Callers receive `NullPointerException` or `IllegalArgumentException` with descriptive messages.

### Service Layer — Optional Chaining

Services use `Optional` to express absent values and chain processing without null checks:

```java
return parseEventType(envelope.eventType())
        .map(eventType -> storeIfNew(envelope, eventType, userId, index))
        .orElseGet(() -> EnvelopeOutcome.invalid(index, "Unknown event type: " + envelope.eventType()));
```

### Optimistic Locking Retry

Projectors handle `OptimisticLockException` with one retry on conflict, logging a `WARN` before retrying.

---

## Log Sanitization

All user-controlled input in log statements is passed through `SecurityUtils` before logging.

**`SecurityUtils.maskDeviceId()`** — shows first 4 + last 4 chars only:

```java
// src/main/java/.../config/SecurityUtils.java
public static String maskDeviceId(String deviceId) {
    return Optional.ofNullable(deviceId)
            .filter(id -> id.length() >= MIN_MASKABLE_LENGTH)  // 8 chars minimum
            .map(SecurityUtils::mask)
            .orElse("***");
}
// "test-device-abc123" → "test...123"
```

**`SecurityUtils.sanitizeForLog()`** — strips `\r\n\t` and truncates at 100 chars:

```java
public static String sanitizeForLog(String input) {
    return Optional.ofNullable(input)
            .map(value -> value.replaceAll("[\\r\\n\\t]", "_"))
            .map(SecurityUtils::truncate)
            .orElse("");
}
```

Usage pattern:

```java
// src/main/java/.../security/HmacAuthenticationFilter.java
log.warn("HMAC authentication failed: {}", SecurityUtils.sanitizeForLog(exception.getMessage()));

// src/main/java/.../config/AppProperties.java
log.info("Loaded HMAC secret for device {}", SecurityUtils.maskDeviceId(deviceId));
```

**Rule:** Never log raw `deviceId`, tokens, secrets, or exception messages containing user input without sanitization.

---

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

---

## Lombok Usage

Lombok is used minimally and only for boilerplate reduction:
- `@Slf4j` — logger field injection in services/filters
- `@RequiredArgsConstructor` — constructor injection for Spring beans
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — JPA protected constructor

**Not used:** `@Getter` on entities (hand-written), `@Setter` (forbidden), `@Data`, `@Builder`.

---

## Gaps & Unknowns

- No `@Valid` / Bean Validation annotations found — it is unclear whether this is a deliberate architectural choice or an oversight for HTTP request bodies
- Error handling in projectors swallows optimistic lock failures after one retry with only a WARN log; no dead-letter queue or alerting observed
- `McpSseForwardController` uses raw HTTP client forwarding; error handling for downstream MCP failures is not evidenced in the scanned code
