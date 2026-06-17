# Walking Skeleton — WalletAssistant Android

**Phase:** 1
**Generated:** 2026-06-17

## Capability Proven End-to-End

A launched Android app signs a request with HMAC-SHA256 and receives HTTP 200 from the running WalletAssistant backend — proving the full auth + networking stack (local.properties → BuildConfig → OkHttp interceptor → Retrofit → backend HmacAuthenticationFilter) works.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Android (Kotlin) + Kotlin DSL Gradle, single module `:app` | Native Android per project constraint (Android only, Jetpack Compose later); Kotlin DSL per D-03 |
| Language split | Java in `net/` package, Kotlin everywhere else | D-02/D-03 — Jetpack Compose requires Kotlin; `net/` mirrors HealthAssistant Java pattern and the HMAC code is copied 1:1 |
| HTTP client | OkHttp 4.12.0 + Retrofit 2.9.0 + Gson 2.10.1 | Versions verified against HealthAssistant production codebase; type-safe, interceptor chain |
| Auth | HMAC-SHA256 as an OkHttp **Application** Interceptor | D-07, AUTH-01 — signs every request automatically (improves on HealthAssistant's per-call signing); identical canonical string to backend |
| Secret management | `local.properties` → `BuildConfig` via `buildConfigField` | AUTH-02, D-05/D-06 — secrets never committed; `local.properties` git-ignored from first commit |
| Device identity | Static `BuildConfig.DEVICE_ID`, NOT `Settings.Secure.ANDROID_ID` | D-07 — ANDROID_ID is per-app scoped on Android 8+; device registered in backend HMAC_DEVICES_JSON |
| Build variants | `debug` + `release` build types, NO product flavors | D-04 — single-flavor app; Cloud Run URL switching deferred to v2 |
| Deployment target | Local emulator / physical device, cleartext HTTP to local backend | Personal-use; Cloud Run + HTTPS deferred to v2 (STATE.md) |
| Directory layout | `app/src/main/java/.../net/` (Java) + `app/src/main/kotlin/...` (Kotlin) | Java/Kotlin split per D-02/D-03; `net/` package holds all networking + auth |
| Repo | Separate repo at `~/AndroidStudioProjects/WalletAssistantAndroid/` | Not a monorepo with the backend (HealthAssistant pattern, project constraint) |
| Async | Kotlin coroutines + `Dispatchers.IO` | D-03; idiomatic replacement for HealthAssistant's raw threads |

## Stack Touched in Phase 1

- [x] Project scaffold (AGP 8.13.0, Gradle 8.13, Kotlin 1.9.20, Kotlin DSL, single `:app` module)
- [x] Routing — single launcher `MainActivity` (no nav yet; bottom nav arrives Phase 4)
- [ ] Database — N/A (app is read-only over HTTP; no local DB, Room out of scope)
- [x] Real backend read — `GET /v1/expenses` against the running backend returns HTTP 200
- [x] UI wired to API — `MainActivity.onCreate` fires the signed call; result logged to Logcat (no Compose UI yet, by design)
- [x] Full-stack run documented — backend `docker-compose up -d && ./gradlew bootRun`; app `./gradlew installDebug`; verify Logcat tag `WalletAuth`

## Out of Scope (Deferred to Later Slices)

- Jetpack Compose UI screens — start in Phase 2 (Expenses Screen)
- MVVM ViewModels, dependency injection — Phase 2 establishes the pattern
- Room DB / offline caching — explicitly out of scope (REQUIREMENTS.md)
- Typed DTOs (`ExpenseResponse`) — Phase 1 uses `List<Object>`; Phase 2 introduces typed models
- Bottom navigation, pull-to-refresh, loading/error/empty states — Phase 4
- Polish currency formatting — Phase 4
- Product flavors / Cloud Run URL switching / HTTPS — v2 milestone
- Android Keystore for secret storage — v2 (BuildConfig in APK accepted for personal use)

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions (HMAC interceptor, BuildConfig secrets, OkHttp/Retrofit assembly, Java/Kotlin split):

- Phase 2: Expenses screen — reverse-chron list, date range picker, category filter chips, total spend card (establishes the Compose + MVVM pattern, replaces `List<Object>` with typed DTOs)
- Phase 3: Accounts + Investments screens — account balances/history and portfolio views reusing the Phase 2 MVVM pattern
- Phase 4: Navigation shell + polish — bottom nav wiring all screens, pull-to-refresh, loading/error/empty states, Polish currency formatting, shippable APK
