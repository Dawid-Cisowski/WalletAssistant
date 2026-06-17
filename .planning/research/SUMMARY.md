# Project Research Summary

**Project:** WalletAssistant Android + Cloud Run Deploy
**Domain:** Personal finance viewer — Android read-only client + Spring Boot event-sourced backend
**Researched:** 2026-06-17
**Confidence:** MEDIUM

## Executive Summary

WalletAssistant is a single-user, read-only Android app that consumes an existing Spring Boot event-sourced backend (already running locally). The scope splits cleanly into two parallel tracks: an Android client (Kotlin + Jetpack Compose) and a Cloud Run deployment of the existing Spring Boot service. The Android app is a pure viewer — expenses, account balances, and investment snapshots — with all data entry handled by Claude AI via MCP. Research confirms that the correct Android architecture is Clean Architecture + MVVM with Hilt DI, Retrofit 3 + OkHttp interceptor for HMAC auth, StateFlow-based UiState, and Navigation Compose for 3-tab bottom navigation.

The biggest recommended approach decision is to use a static `BuildConfig.DEVICE_ID` from `local.properties` rather than dynamic `ANDROID_ID`. Every other personal-finance Android pattern is well-established: sealed `UiState`, repository-as-async-boundary, stateless leaf composables, pull-to-refresh as the only write-side user action. On the Cloud Run side, two non-obvious requirements dominate: Cloud SQL requires the socket factory connector (plain JDBC fails), and `min-instances=1` is mandatory to prevent 10-15 second cold starts that will time out Android's OkHttp before the JVM warms up.

The key risk cluster is HMAC authentication: four distinct pitfalls (per-app ANDROID_ID scoping, debug/release keystore ANDROID_ID differences, Base64 encoding format, timestamp skew) all manifest as silent 401s with no actionable error message. These must be resolved in Phase 1 before any UI work begins, because every API call depends on correct HMAC signing. The Cloud Run deploy has its own pitfall cluster (Cloud SQL connector, HikariCP pool sizing, Flyway race condition, health check path) that should be addressed as a dedicated phase after the Android client is functional locally.

## Key Findings

### Recommended Stack

**Android:**
- Kotlin 2.1.21 + Compose BOM 2025.12.00 (Material3 1.4.0)
- Hilt 2.59 + KSP — compile-time DI; errors at build time not runtime
- Retrofit 3.0.0 + OkHttp 4.12.0 — native suspend support; HMAC interceptor is a clean single integration point
- Moshi 1.15.2 with codegen — null-safe, no reflection; preferred over Gson
- Navigation Compose 2.x — stable 3-tab bottom nav with `saveState`/`restoreState`
- StateFlow + `collectAsStateWithLifecycle()` — battery-correct UI state; pauses in background

**Cloud Run:**
- Docker multi-stage layered JARs
- Google Artifact Registry + Cloud Build for CI/CD
- Secret Manager for all secrets (`HMAC_DEVICES_JSON`, `SPRING_DATASOURCE_PASSWORD`, `API_KEY`)
- Cloud SQL with postgres socket factory connector — mandatory, plain JDBC URL fails
- `min-instances=1` — eliminates cold start (costs ~$5-10/month)
- `maximum-pool-size=2`, `max-instances=3` — prevents HikariCP pool exhaustion

### Expected Features

**Table stakes (must have):**
- HMAC auth OkHttp interceptor — prerequisite for all API calls
- Bottom navigation: 3 tabs (Expenses / Accounts / Investments)
- Shared `UiState` sealed class (Loading / Success / Error) — reused on all 3 screens
- Expenses: reverse-chron list, monthly default, date range selector, category filter chips, total spend card
- Accounts: balance card per account type (BUSINESS / PERSONAL_SAVINGS / PERSONAL_SPENDING), balance history list
- Investments: total portfolio value headline, per-type breakdown (IKE, XTB_STOCKS, XTB_ETF, SAVINGS_ACCOUNT, CRYPTO, OTHER)
- Pull-to-refresh on all screens
- Polish locale currency formatting ("1 234,56 zł")
- Error distinction: network timeout vs auth failure (401/403)

**Differentiators (v2+):**
- Category spending donut/bar chart (Vico)
- Balance history sparkline per account
- Portfolio value history line chart
- Month-over-month expense comparison
- Dark mode (near-free with Material3 dynamic color)

**Anti-features (never build for v1):**
- Write UI (add/edit expenses) — Claude AI via MCP handles this
- Offline mode / Room DB caching
- Push notifications
- Transaction text search (no backend full-text endpoint)
- CSV/PDF export — Claude AI covers this

### Architecture Approach

Clean Architecture with three layers: Presentation (Screen composables + ViewModels + StateFlow), thin Domain layer (domain models + mappers), and Data (Repositories + Retrofit ApiService + HmacSigningInterceptor).

**Major components:**
1. `HmacSigningInterceptor` — OkHttp application-level interceptor; signs every request; HMAC secret from `BuildConfig` at build time
2. `NetworkModule` (Hilt `SingletonComponent`) — provides `OkHttpClient`, `Retrofit`, `WalletAssistantApiService`, and all three Repository singletons
3. `*Repository` (3) — maps DTOs to domain models, wraps network calls in `Result<T>`, the only async boundary
4. `*ViewModel` (3) — holds `StateFlow<UiState>`, calls repository in `viewModelScope`, drives Loading/Success/Error transitions
5. `*Screen` composables (3) — observe ViewModel state via `collectAsStateWithLifecycle()`; stateless leaf composables receive plain data
6. `MainNavGraph` — `Scaffold` + `NavigationBar` + `NavHost`; `saveState`/`restoreState` on tab switches

**Strict build order:** `HmacSigningInterceptor` → `NetworkModule` → DTOs + Repositories → ViewModels → Screen composables → NavGraph → `MainActivity`

### Critical Pitfalls

1. **ANDROID_ID is per-app scoped (Android 8+)** — Use `BuildConfig.DEVICE_ID` from `local.properties` instead of `Settings.Secure.ANDROID_ID`. Register a new entry in `HMAC_DEVICES_JSON` for WalletAssistant — the HealthAssistant device ID will NOT work.
2. **Cloud SQL requires socket factory — plain JDBC URL fails** — Add `com.google.cloud.sql:postgres-socket-factory`, use socket factory JDBC URL, grant Cloud Run service account `Cloud SQL Client` IAM role.
3. **Cloud Run cold start 10-15s exceeds OkHttp default 10s timeout** — Set `--min-instances=1` AND set OkHttp timeouts to 30s.
4. **Base64 encoding format mismatch** — Use `Base64.NO_WRAP` exclusively; never `Base64.DEFAULT`. Copy `HmacUtil` from HealthAssistant without modification.
5. **HikariCP pool × Cloud Run instances exceeds PostgreSQL `max_connections`** — Set `maximum-pool-size=2` and `--max-instances=3`; caps total connections at 6.
6. **Spring Boot 4 / Spring Security 7 CSRF** — Verify backend `SecurityFilterChain` has explicit `csrf(csrf -> csrf.disable())` before Cloud Run deploy.
7. **`local.properties` must be in `.gitignore` from day one** — `HMAC_SECRET` in BuildConfig is baked into APK (acceptable for personal use) but must never be committed.

## Implications for Roadmap

### Suggested Phase Structure (5 phases)

**Phase 1: Android Foundation — HMAC Auth + Networking**
- Android project scaffold (Hilt, Retrofit, Moshi, Timber, DataStore)
- `HmacSigningInterceptor` + `NetworkModule` + `WalletAssistantApiService`
- Verified HMAC-signed request against real backend
- Resolves all 4 HMAC pitfalls before any UI work
- Standard patterns — no extra research needed

**Phase 2: Expenses Screen**
- `ExpensesRepository`, `ExpensesViewModel`, `ExpensesScreen`
- Full table-stakes feature set: list, date range, category filters, total spend card
- Establishes shared `UiState` sealed class + loading/error/empty composables for reuse
- Validates full MVVM pattern end-to-end

**Phase 3: Accounts + Investments Screens**
- `AccountsRepository`, `AccountsViewModel`, `AccountsScreen`
- `InvestmentsRepository`, `InvestmentsViewModel`, `InvestmentsScreen`
- Identical MVVM pattern to Phase 2; simpler (no date/category filtering)

**Phase 4: Navigation Shell + Polish**
- `MainNavGraph` + `Scaffold` + `NavigationBar` + `MainActivity`
- Dark mode, Polish locale currency formatting, consistent Material3 theme
- Complete shippable APK
- **Research flag:** Verify Nav3 vs Nav2 stability at planning time — STACK.md and ARCHITECTURE.md disagree

**Phase 5: Cloud Run Deploy**
- Dockerized Spring Boot on Cloud Run
- Cloud SQL via socket factory + IAM service account
- Secret Manager for all secrets
- Cloud Build CI/CD pipeline
- `min-instances=1`, `maximum-pool-size=2`, `max-instances=3`
- Health check at `/actuator/health`
- Flyway migration safety (`max-instances=1` during schema changes)
- **Research flag:** Cloud SQL socket factory + IAM setup is non-trivial GCP configuration

### Phase Ordering Rationale

- Phases 1-4 follow strict build order from ARCHITECTURE.md: networking → screens → navigation
- Phase 5 (Cloud Run) is intentionally last — verify Android locally first, test against production immediately after deploy
- HMAC pitfalls cluster entirely in Phase 1; Cloud Run pitfalls cluster entirely in Phase 5 — no cross-contamination
- Phase 2 establishes the MVVM pattern once; Phase 3 replicates it without re-litigating architecture

## Sources

### Primary (HIGH confidence)
- Android Developers — Compose UI Architecture
- Android Developers — Dependency Injection with Hilt
- Android Developers — Navigation with Compose
- Google Cloud — Optimize Java Applications for Cloud Run
- Google Cloud — Connect from Cloud Run to Cloud SQL PostgreSQL
- Google Cloud — Configure Secrets for Cloud Run

### Secondary (MEDIUM confidence)
- Jetpack Compose BOM 2025.12.00 release notes
- Navigation 3 stable announcement (Google Developers Blog)
- Retrofit 3.0.0 release (GitHub)
- Spring Boot 4 Migration Breaking Changes
- Android Developers Blog — Changes to Device Identifiers in Android O

---
*Research completed: 2026-06-17*
*Ready for roadmap: yes*
