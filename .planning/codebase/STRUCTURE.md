# Codebase Structure

> Last updated: 2026-06-17 | Source: codebase scan

## Summary

WalletAssistant is a single Gradle project with a separate `integration-tests/` subproject. All production Java source lives under one base package. Each Spring Modulith module owns a package under `walletassistant/` and exposes only its `api/` subpackage publicly.

## Directory Layout

```
WalletAssistant/
├── src/
│   ├── main/
│   │   ├── java/org/dawid/cisowski/walletassistant/
│   │   │   ├── WalletAssistantApplication.java      # Spring Boot entry point
│   │   │   ├── config/                              # Cross-cutting config beans
│   │   │   │   ├── AppProperties.java               # @ConfigurationProperties root
│   │   │   │   ├── SecurityConfig.java              # Spring Security configuration
│   │   │   │   ├── OpenApiConfig.java               # Springdoc OpenAPI config
│   │   │   │   ├── McpCorsConfig.java               # CORS for MCP SSE endpoint
│   │   │   │   ├── SecurityUtils.java               # Log sanitization utilities
│   │   │   │   └── package-info.java
│   │   │   ├── security/                            # Auth filter module
│   │   │   │   ├── api/                             # PUBLIC
│   │   │   │   │   ├── DeviceSecretProvider.java    # Interface: device secret lookup
│   │   │   │   │   └── NonceCache.java              # Interface: replay protection
│   │   │   │   ├── ApiKeyAuthenticationFilter.java  # package-private
│   │   │   │   ├── HmacAuthenticationFilter.java    # package-private
│   │   │   │   ├── HmacSignature.java               # package-private
│   │   │   │   ├── NonceCacheAdapter.java           # package-private
│   │   │   │   ├── DeviceSecretProviderAdapter.java # package-private
│   │   │   │   ├── CachedBodyHttpServletRequest.java
│   │   │   │   ├── CacheConfig.java
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   └── package-info.java
│   │   │   ├── walletevents/                        # Write-side module
│   │   │   │   ├── api/                             # PUBLIC
│   │   │   │   │   ├── WalletEventsFacade.java      # Interface + nested command/result records
│   │   │   │   │   ├── EventType.java               # Enum: all event type names
│   │   │   │   │   ├── StoredEventData.java         # Record: event data passed in domain events
│   │   │   │   │   ├── ExpensesStoredEvent.java     # Domain event record
│   │   │   │   │   ├── AccountSnapshotsStoredEvent.java
│   │   │   │   │   ├── InvestmentSnapshotsStoredEvent.java
│   │   │   │   │   └── package-info.java
│   │   │   │   ├── WalletEventsService.java         # package-private (implements WalletEventsFacade)
│   │   │   │   ├── WalletEventsController.java      # package-private
│   │   │   │   ├── WalletEvent.java                 # package-private domain object
│   │   │   │   ├── WalletEventJpaEntity.java        # package-private JPA entity
│   │   │   │   └── WalletEventsRepository.java      # package-private
│   │   │   ├── expenses/                            # Projection module
│   │   │   │   ├── api/                             # PUBLIC
│   │   │   │   │   ├── ExpensesFacade.java
│   │   │   │   │   ├── ExpenseResponse.java         # Record DTO
│   │   │   │   │   ├── MonthlyExpenseSummaryResponse.java
│   │   │   │   │   └── package-info.java
│   │   │   │   ├── ExpensesEventsListener.java      # @ApplicationModuleListener
│   │   │   │   ├── ExpensesProjector.java
│   │   │   │   ├── ExpensesService.java
│   │   │   │   ├── ExpensesController.java
│   │   │   │   ├── Expense.java                     # Domain object
│   │   │   │   ├── ExpenseProjectionJpaEntity.java
│   │   │   │   ├── ExpensesRepository.java
│   │   │   │   └── ExpenseCategory.java             # Enum
│   │   │   ├── accounts/                            # Projection module
│   │   │   │   ├── api/
│   │   │   │   │   ├── AccountsFacade.java
│   │   │   │   │   ├── AccountBalanceResponse.java
│   │   │   │   │   └── package-info.java
│   │   │   │   ├── AccountsEventsListener.java
│   │   │   │   ├── AccountsProjector.java
│   │   │   │   ├── AccountsService.java
│   │   │   │   ├── AccountsController.java
│   │   │   │   ├── BalanceSnapshot.java
│   │   │   │   ├── BalanceSnapshotJpaEntity.java
│   │   │   │   ├── AccountsRepository.java
│   │   │   │   └── AccountType.java                 # Enum
│   │   │   ├── investments/                         # Projection module
│   │   │   │   ├── api/
│   │   │   │   │   ├── InvestmentsFacade.java
│   │   │   │   │   ├── InvestmentResponse.java
│   │   │   │   │   ├── PortfolioSummaryResponse.java
│   │   │   │   │   └── package-info.java
│   │   │   │   ├── InvestmentsEventsListener.java
│   │   │   │   ├── InvestmentsProjector.java
│   │   │   │   ├── InvestmentsService.java
│   │   │   │   ├── InvestmentsController.java
│   │   │   │   ├── InvestmentSnapshot.java
│   │   │   │   ├── InvestmentSnapshotJpaEntity.java
│   │   │   │   ├── InvestmentsRepository.java
│   │   │   │   └── InvestmentType.java              # Enum
│   │   │   └── mcp/                                 # MCP integration module
│   │   │       ├── WalletTools.java                 # Spring AI MCP tool definitions
│   │   │       ├── McpSseForwardController.java     # SSE → /mcp forwarding
│   │   │       ├── McpToolsConfiguration.java
│   │   │       └── package-info.java
│   │   └── resources/
│   │       ├── application.yml                      # Main config
│   │       ├── application-test.yml                 # Test profile overrides
│   │       └── db/migration/
│   │           ├── V1__create_wallet_events_table.sql
│   │           ├── V2__create_expense_projections_table.sql
│   │           ├── V3__create_account_balance_snapshots_table.sql
│   │           └── V4__create_investment_snapshots_table.sql
│   └── test/
│       ├── groovy/org/dawid/cisowski/walletassistant/
│       │   ├── expenses/                            # Unit specs
│       │   ├── security/                            # Unit specs
│       │   └── walletevents/                        # Unit specs
│       └── java/org/dawid/cisowski/walletassistant/
│           └── ModularityTests.java                 # Spring Modulith boundary verification
├── integration-tests/                               # Separate Gradle subproject
│   └── src/test/groovy/org/dawid/cisowski/walletassistant/
│       └── [integration Spock specs]                # Testcontainers + rest-assured
├── build.gradle.kts                                 # Root Gradle build (Kotlin DSL)
├── settings.gradle.kts
├── docker-compose.yml                               # PostgreSQL for local dev
└── CLAUDE.md
```

## Module Layout Convention

Every module follows this pattern:

```
<module>/
  api/                     # PUBLIC — visible to other modules
    <Module>Facade.java    # Interface defining the module's cross-module API
    <ResponseDTO>.java     # Records (immutable DTOs)
    <DomainEvent>.java     # Domain event records (for walletevents only)
    package-info.java      # @ApplicationModule annotation lives here
  <Module>Service.java     # package-private — implements the Facade
  <Module>Controller.java  # package-private — REST endpoints
  <Module>Repository.java  # package-private — Spring Data JPA
  <Module>Entity.java      # package-private — JPA entity (e.g., WalletEventJpaEntity)
  <Module>Projector.java   # package-private — projection logic (projection modules only)
  <Module>EventsListener.java # package-private — @ApplicationModuleListener (projection modules only)
  <DomainObject>.java      # package-private — domain model (e.g., Expense, BalanceSnapshot)
  <Type>Enum.java          # package-private — domain enums (e.g., ExpenseCategory, AccountType)
```

## Package Naming Conventions

| Layer | Pattern | Example |
|-------|---------|---------|
| Base package | `org.dawid.cisowski.walletassistant` | — |
| Module | `org.dawid.cisowski.walletassistant.<module>` | `.walletevents` |
| Public API | `org.dawid.cisowski.walletassistant.<module>.api` | `.walletevents.api` |

Modules: `walletevents`, `expenses`, `accounts`, `investments`, `mcp`, `security`, `config`

## File Naming Patterns

| Type | Pattern | Example |
|------|---------|---------|
| JPA Entity | `<Domain>JpaEntity.java` | `WalletEventJpaEntity.java` |
| Domain object | `<Domain>.java` | `WalletEvent.java`, `Expense.java` |
| Facade interface | `<Module>Facade.java` | `WalletEventsFacade.java` |
| Event listener | `<Module>EventsListener.java` | `ExpensesEventsListener.java` |
| Projector | `<Module>Projector.java` | `ExpensesProjector.java` |
| DTO Record | `<Concept>Response.java` or `<Concept>Event.java` | `ExpenseResponse.java` |
| Flyway migration | `V<N>__<description>.sql` | `V1__create_wallet_events_table.sql` |
| Spock unit spec | `<Subject>Spec.groovy` | (in `src/test/groovy/`) |

## Key File Locations

**Entry Point:**
- `src/main/java/org/dawid/cisowski/walletassistant/WalletAssistantApplication.java`

**Configuration:**
- `src/main/resources/application.yml` — all runtime config, env var bindings
- `src/main/resources/application-test.yml` — test profile overrides
- `src/main/java/.../config/AppProperties.java` — typed config binding

**Database Migrations:**
- `src/main/resources/db/migration/V*.sql` — Flyway scripts, versioned sequentially

**Modularity Test:**
- `src/test/java/org/dawid/cisowski/walletassistant/ModularityTests.java` — enforces module boundary rules

**Integration Tests:**
- `integration-tests/src/test/groovy/` — Testcontainers + rest-assured Spock specs

**Unit Tests:**
- `src/test/groovy/org/dawid/cisowski/walletassistant/<module>/` — Spock unit specs

**MCP Tools:**
- `src/main/java/.../mcp/WalletTools.java` — Spring AI MCP tool definitions

## Where to Add New Code

**New event type:**
1. Add to `walletevents/api/EventType.java`
2. Add handling in `WalletEventsService.publishGrouped()` if it's a new category
3. Create a new domain event record in `walletevents/api/` if a new projection module needs it

**New projection module:**
1. Create `src/main/java/.../walletassistant/<module>/` directory
2. Add `api/` subpackage with a `package-info.java` and Facade interface
3. Create `<Module>EventsListener.java` with `@ApplicationModuleListener`
4. Create `<Module>Projector.java`, `<Module>Service.java`, `<Module>Controller.java`, `<Module>Repository.java`
5. Add Flyway migration in `src/main/resources/db/migration/`
6. Add integration tests in `integration-tests/src/test/groovy/`

**New REST endpoint in an existing module:**
1. Add method to the module's `api/<Module>Facade.java` interface
2. Implement in the package-private `<Module>Service.java`
3. Expose in package-private `<Module>Controller.java`
4. Keep all new classes package-private unless cross-module access is needed

**New configuration property:**
1. Add to `src/main/resources/application.yml` with `${ENV_VAR:default}` binding
2. Add typed field to `src/main/java/.../config/AppProperties.java`

**New Flyway migration:**
1. Add `V<N+1>__<description>.sql` in `src/main/resources/db/migration/`
2. Never modify existing migration files

**New MCP tool:**
1. Add to `src/main/java/.../mcp/WalletTools.java` using Spring AI `@Tool` annotation

## Special Directories

**`.planning/codebase/`:**
- Purpose: GSD codebase analysis documents
- Generated: Yes (by gsd-map-codebase)
- Committed: Optional

**`integration-tests/`:**
- Purpose: Separate Gradle subproject for integration tests
- Requires: Running PostgreSQL (via docker-compose or Testcontainers)
- Run with: `./gradlew :integration-tests:test`

**`build/`:**
- Generated Gradle build output; not committed

---

*Structure analysis: 2026-06-17*
