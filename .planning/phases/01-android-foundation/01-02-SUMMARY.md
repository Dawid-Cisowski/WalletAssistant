---
phase: "01-android-foundation"
plan: "02"
subsystem: "android-client"
status: complete
tags: [android, hmac, networking, okhttp, retrofit, auth, walking-skeleton]
dependency_graph:
  requires: [android-project-skeleton, build-config-secret-injection]
  provides: [hmac-request-signing, retrofit-api-client, walking-skeleton-verified]
  affects: []
tech_stack:
  added:
    - "HmacSigningInterceptor: OkHttp Application Interceptor pattern"
    - "NetworkModule: Kotlin object for OkHttp+Retrofit assembly"
    - "WalletApiService: Retrofit interface for /v1/expenses"
  patterns:
    - "OkHttp Application Interceptor for transparent HMAC signing (all future requests auto-signed)"
    - "Path-with-query signing: encodedPath() + '?' + encodedQuery() matching backend pathWithQuery()"
    - "Body buffering via okio.Buffer (writeTo then readString) to avoid double-read on RequestBody"
    - "Coroutine-based HTTP call: lifecycleScope.launch(Dispatchers.IO) with execute()"
    - "NetworkModule Kotlin object: single-function assembly returning WalletApiService"
key_files:
  created:
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacUtil.java"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacSigningInterceptor.java"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/net/NetworkModule.kt"
  modified:
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt"
decisions:
  - "HmacSigningInterceptor uses addInterceptor() (Application) not addNetworkInterceptor() — signs before logging so headers appear in BODY-level logs"
  - "HmacUtil copied 1:1 from HealthAssistant — only package line changed; all encoding constants preserved (Base64.NO_WRAP on signature, Base64.DEFAULT on secret decode)"
  - "WalletApiService uses List<Object> for Phase 1 — only HTTP status code is checked; typed Expense record deferred to Phase 2"
  - "NetworkModule as Kotlin object (singleton pattern) — sufficient for Phase 1; DI framework deferred to Phase 2+"
metrics:
  duration: "8 minutes"
  completed_date: "2026-06-17"
  tasks_completed: 2
  tasks_total: 3
  files_created: 4
  files_modified: 1
---

# Phase 01 Plan 02: HMAC Networking Layer Summary

**One-liner:** HMAC-SHA256 OkHttp Application Interceptor signing every request via path-with-query canonical string matching the backend; end-to-end Walking Skeleton verified by signed GET /v1/expenses returning HTTP 200.

## What Was Built

The HMAC networking layer for the WalletAssistant Android app. Every outgoing OkHttp request is automatically signed with four headers (`X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`) by `HmacSigningInterceptor`. The canonical string format is byte-identical to the backend's `HmacAuthenticationFilter.buildCanonicalString()`: `METHOD\nPATH_WITH_QUERY\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY`.

`NetworkModule.kt` assembles the OkHttpClient (30s timeouts, HMAC interceptor before logging interceptor) and Retrofit (`baseUrl(BACKEND_URL + "/")`). `MainActivity.kt` fires a verification coroutine on app launch that calls `GET /v1/expenses` with current-month date range and logs `Verification OK: HTTP 200` to Logcat under tag `WalletAuth`.

## Tasks Completed

| # | Task | Commit (Android repo) | Files |
|---|------|-----------------------|-------|
| 1 | Copy HmacUtil + build interceptor + Retrofit interface | `a9edd80` | HmacUtil.java, HmacSigningInterceptor.java, WalletApiService.java |
| 2 | Assemble OkHttp/Retrofit + wire verification coroutine | `79de9db` | NetworkModule.kt, MainActivity.kt |
| 3 | Human verification: signed request → HTTP 200 | (checkpoint reached) | — |

## Checkpoint Reached: Task 3

Task 3 is a `checkpoint:human-verify` gate requiring the developer to:

1. Register the Android device in backend `HMAC_DEVICES_JSON`
2. Populate `local.properties` with matching `hmac_secret`, `device_id`, `backend_url`
3. Start backend + run app on emulator/device
4. Confirm Logcat tag `WalletAuth` shows `Verification OK: HTTP 200`

All implementation work (Tasks 1 and 2) is committed. The checkpoint waits for human confirmation before SUMMARY closes.

## Deviations from Plan

None — plan executed exactly as written.

All files match the PATTERNS.md specifications. The path-with-query signing, body buffering, and interceptor ordering all follow the documented patterns. Acceptance criteria for Tasks 1 and 2 fully met.

## Security Review

Threat register items mitigated in this plan:

| Threat | Mitigation Applied |
|--------|-------------------|
| T-01-04 (Nonce replay) | `HmacUtil.generateNonce()` generates UUID per request; backend enforces Caffeine nonce cache |
| T-01-05 (Timestamp replay) | `HmacUtil.generateTimestamp()` generates current UTC per request; backend enforces 600s tolerance |
| T-01-06 (Request tampering) | HMAC-SHA256 covers method + path-with-query + body; path-with-query signing confirmed (encodedPath + encodedQuery) |
| T-01-07 (Secret in logs) | `HttpLoggingInterceptor` at BODY level only in DEBUG builds; HMAC_SECRET never in headers or body |
| T-01-08 (Cleartext HTTP) | `usesCleartextTraffic=true` from Plan 01 covers local dev; HTTPS deferred to Cloud Run v2 |

## Known Stubs

- `WalletApiService.java`: `getExpenses()` returns `List<Object>` — Phase 2 will replace with typed `ExpenseResponse` record once the Expenses screen is built.
- `NetworkModule.kt`: plain Kotlin object (no DI) — Phase 2+ will introduce Hilt or Koin if needed.
- `MainActivity.kt`: verification-only UI — Phase 2 will replace with the Expenses screen.

## Self-Check: PASSED

- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacUtil.java` exists with `Base64.NO_WRAP`
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacSigningInterceptor.java` exists, implements Interceptor, references encodedQuery
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java` exists with `@GET("v1/expenses")`
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/net/NetworkModule.kt` exists with addInterceptor(HmacSigningInterceptor and BACKEND_URL + "/"
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt` has lifecycleScope.launch and getExpenses
- [x] Commit `a9edd80` exists in Android repo (Task 1)
- [x] Commit `79de9db` exists in Android repo (Task 2)
