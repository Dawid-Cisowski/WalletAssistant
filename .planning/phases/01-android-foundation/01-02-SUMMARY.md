---
phase: "01-android-foundation"
plan: "02"
subsystem: "android-client"
status: complete
tags: [android, hmac, networking, okhttp, retrofit, auth, walking-skeleton]
dependency_graph:
  requires:
    - phase: "01-01"
      provides: "android-project-skeleton, build-config-secret-injection (BACKEND_URL, HMAC_SECRET, DEVICE_ID via BuildConfig)"
  provides:
    - "hmac-request-signing"
    - "retrofit-api-client"
    - "walking-skeleton-verified"
  affects: ["Phase 2 Expenses Screen"]
tech_stack:
  added:
    - "HmacSigningInterceptor: OkHttp Application Interceptor pattern"
    - "NetworkModule: Kotlin object for OkHttp+Retrofit assembly"
    - "WalletApiService: Retrofit interface for /v1/expenses"
    - "gradle.properties: android.useAndroidX=true (required by AGP 8.x)"
  patterns:
    - "OkHttp Application Interceptor for transparent HMAC signing (all future requests auto-signed)"
    - "Path-with-query signing: encodedPath() + '?' + encodedQuery() matching backend pathWithQuery()"
    - "Body buffering via okio.Buffer (writeTo then readString) to avoid double-read on RequestBody"
    - "Coroutine-based HTTP call: lifecycleScope.launch(Dispatchers.IO) with execute()"
    - "NetworkModule Kotlin object: single-function assembly returning WalletApiService"
    - "Device ID read from Settings.Secure.ANDROID_ID at runtime — no static BuildConfig field needed"
key_files:
  created:
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacUtil.java"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacSigningInterceptor.java"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/net/NetworkModule.kt"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/gradle.properties"
  modified:
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt"
decisions:
  - "HmacSigningInterceptor uses addInterceptor() (Application) not addNetworkInterceptor() — signs before logging so headers appear in BODY-level logs"
  - "HmacUtil copied 1:1 from HealthAssistant — only package line changed; all encoding constants preserved (Base64.NO_WRAP on signature, Base64.DEFAULT on secret decode)"
  - "WalletApiService uses List<Object> for Phase 1 — only HTTP status code is checked; typed Expense record deferred to Phase 2"
  - "NetworkModule as Kotlin object (singleton pattern) — sufficient for Phase 1; DI framework deferred to Phase 2+"
  - "Device ID read from Settings.Secure.ANDROID_ID at runtime — avoids stale value, stable across reinstalls on Android 8+"
  - "BACKEND_URL guard added in NetworkModule — throws IllegalStateException with clear message when local.properties not populated"
metrics:
  duration: "~2 hours (including human verification checkpoint)"
  completed_date: "2026-06-18"
  tasks_completed: 3
  tasks_total: 3
  files_created: 5
  files_modified: 1
requirements_completed: [AUTH-01, AUTH-02]
---

# Phase 01 Plan 02: HMAC Networking Layer Summary

**HMAC-SHA256 OkHttp Application Interceptor signing every request via path-with-query canonical string matching the backend; end-to-end Walking Skeleton verified by signed GET /v1/expenses returning HTTP 200 confirmed in Logcat.**

## What Was Built

The complete HMAC networking layer for the WalletAssistant Android app. Every outgoing OkHttp request is automatically signed with four headers (`X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`) by `HmacSigningInterceptor`. The canonical string format is byte-identical to the backend's `HmacAuthenticationFilter.buildCanonicalString()`: `METHOD\nPATH_WITH_QUERY\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY`.

`NetworkModule.kt` assembles the OkHttpClient (30s timeouts, HMAC interceptor before logging interceptor) and Retrofit (`baseUrl(BACKEND_URL + "/")`). `MainActivity.kt` fires a verification coroutine on app launch that calls `GET /v1/expenses` with current-month date range and logs `Verification OK: HTTP 200` to Logcat under tag `WalletAuth`.

The Walking Skeleton is now verified end-to-end: scaffold → BuildConfig → interceptor → Retrofit → backend returns HTTP 200.

## Tasks Completed

| # | Task | Commit (Android repo) | Files |
|---|------|-----------------------|-------|
| 1 | Copy HmacUtil + build interceptor + Retrofit interface | `a9edd80` | HmacUtil.java, HmacSigningInterceptor.java, WalletApiService.java |
| 2 | Assemble OkHttp/Retrofit + wire verification coroutine | `79de9db` | NetworkModule.kt, MainActivity.kt |
| 2a | gradle.properties with android.useAndroidX=true (deviation fix) | `118fd2b` | gradle.properties |
| 2b | Drop named args on Java interface call in MainActivity (deviation fix) | `ca30e4b` | MainActivity.kt |
| 2c | Add BACKEND_URL guard in NetworkModule (deviation fix) | `64b3eb4` | NetworkModule.kt |
| 2d | Read device ID from ANDROID_ID at runtime (deviation fix) | `b95233a` | HmacSigningInterceptor.java, NetworkModule.kt, MainActivity.kt |
| 3 | Human verification: signed request → HTTP 200 | APPROVED | — |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `android.useAndroidX` missing from gradle.properties**
- **Found during:** Task 2 (gradle assembleDebug)
- **Issue:** AndroidX dependencies required `android.useAndroidX=true` in `gradle.properties`; AGP 8.x raised a configuration error without it
- **Fix:** Created `gradle.properties` with `android.useAndroidX=true` and `android.enableJetifier=false`
- **Files modified:** `~/AndroidStudioProjects/WalletAssistantAndroid/gradle.properties`
- **Commit:** `118fd2b`

**2. [Rule 1 - Bug] Named arguments not allowed on Java interface call in Kotlin**
- **Found during:** Task 2 (Kotlin compilation of MainActivity)
- **Issue:** Kotlin compiler error — named arguments cannot be used on a call to a Java method (`getExpenses(from = ..., to = ...)`)
- **Fix:** Replaced named args with positional args: `api.getExpenses(from, to)`
- **Files modified:** `MainActivity.kt`
- **Commit:** `ca30e4b`

**3. [Rule 2 - Missing Critical] No guard for empty BACKEND_URL in NetworkModule**
- **Found during:** Task 2 verification run — silent failure when credentials not populated
- **Issue:** If `local.properties` is not populated, `BuildConfig.BACKEND_URL` is an empty string and Retrofit throws an opaque error without telling the developer what is missing
- **Fix:** Added `require(BuildConfig.BACKEND_URL.isNotEmpty()) { "BACKEND_URL not set in local.properties" }` before Retrofit construction
- **Files modified:** `NetworkModule.kt`
- **Commit:** `64b3eb4`

**4. [Rule 2 - Improvement] Device ID read from ANDROID_ID at runtime instead of static BuildConfig field**
- **Found during:** Task 3 human verification setup
- **Issue:** Using a static `BuildConfig.DEVICE_ID` forced the developer to coordinate a specific string in both `local.properties` and backend `HMAC_DEVICES_JSON`, and a stale value would persist across app reinstalls
- **Fix:** Changed `HmacSigningInterceptor` to accept `Context` and read `Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)` at runtime
- **Files modified:** `HmacSigningInterceptor.java`, `NetworkModule.kt`, `MainActivity.kt`
- **Commit:** `b95233a`

---

**Total deviations:** 4 auto-fixed (1 blocking build error, 1 compilation bug, 2 missing critical/improvement)
**Impact on plan:** All fixes necessary for build correctness and developer ergonomics. Core HMAC signing logic, Retrofit interface, and verification flow match plan specification exactly.

## Issues Encountered

**Backend device registration (RESEARCH.md Pitfall 1):** The device ID in the app must match exactly the key registered in backend `HMAC_DEVICES_JSON`. During the human verification checkpoint, the developer registered `wallet-android-device` (the value of `ANDROID_ID`) in the backend env var and restarted `./gradlew bootRun` before the request returned HTTP 200. This is expected behavior documented in the plan's `user_setup` section.

**local.properties credentials:** `local.properties` is gitignored and must be manually populated before each new machine setup. `local.properties.example` (from Plan 01) serves as the template.

## Security Review

| Threat | Status | Mitigation Applied |
|--------|--------|-------------------|
| T-01-04 Nonce replay | Mitigated | `HmacUtil.generateNonce()` generates UUID per request; backend enforces Caffeine nonce cache (600s TTL) |
| T-01-05 Timestamp replay | Mitigated | `HmacUtil.generateTimestamp()` generates current UTC per request; backend enforces 600s tolerance window |
| T-01-06 Request tampering | Mitigated | HMAC-SHA256 covers method + path-with-query + body; path-with-query signing confirmed (`encodedPath + '?' + encodedQuery`) |
| T-01-07 Secret in logs | Accepted | `HttpLoggingInterceptor` at BODY level only in DEBUG builds; HMAC_SECRET never in headers or body — only derived signature |
| T-01-08 Cleartext HTTP | Accepted | `usesCleartextTraffic=true` from Plan 01 covers local dev; HTTPS deferred to Cloud Run v2 |

## Known Stubs

- `WalletApiService.java`: `getExpenses()` returns `List<Object>` — Phase 2 will replace with typed `ExpenseResponse` record once the Expenses screen is built.
- `NetworkModule.kt`: plain Kotlin object (no DI) — Phase 2+ will introduce Hilt or Koin if needed.
- `MainActivity.kt`: verification-only coroutine — Phase 2 will replace with the Expenses screen ViewModel + Compose UI.

All stubs are intentional for the Walking Skeleton phase. They are resolved in Phase 2 (Expenses Screen).

## Next Phase Readiness

- Walking Skeleton verified end-to-end (Logcat: `Verification OK: HTTP 200`)
- AUTH-01 and AUTH-02 requirements satisfied
- Phase 1 all success criteria TRUE:
  1. Signed HMAC request returns HTTP 200 — confirmed
  2. Credentials from `local.properties` via `BuildConfig` (BACKEND_URL, HMAC_SECRET) and `ANDROID_ID` at runtime — confirmed
  3. `local.properties` gitignored from first commit — confirmed (Plan 01)
  4. `HmacSigningInterceptor` wired into `OkHttpClient` signing every outgoing request — confirmed
- Phase 2 (Expenses Screen) can begin immediately — MVVM pattern can build on top of `WalletApiService`
- No blockers

## Self-Check: PASSED

- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacUtil.java` exists with `Base64.NO_WRAP`
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacSigningInterceptor.java` exists, implements Interceptor, reads ANDROID_ID, references encodedQuery
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java` exists with `@GET("v1/expenses")`
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/net/NetworkModule.kt` exists with `addInterceptor(HmacSigningInterceptor` and `BACKEND_URL + "/"`
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt` has `lifecycleScope.launch` and `getExpenses`
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/gradle.properties` exists with `android.useAndroidX=true`
- [x] Commit `a9edd80` exists in Android repo (Task 1)
- [x] Commit `79de9db` exists in Android repo (Task 2)
- [x] Commit `118fd2b` exists in Android repo (deviation fix 1)
- [x] Commit `ca30e4b` exists in Android repo (deviation fix 2)
- [x] Commit `64b3eb4` exists in Android repo (deviation fix 3)
- [x] Commit `b95233a` exists in Android repo (deviation fix 4)
- [x] Human verification: user confirmed `Verification OK: HTTP 200` in Logcat
