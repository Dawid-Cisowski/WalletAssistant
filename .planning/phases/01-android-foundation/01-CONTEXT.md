# Phase 1: Android Foundation - Context

**Gathered:** 2026-06-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Create a new Android project at `~/AndroidStudioProjects/WalletAssistantAndroid/` with HMAC-SHA256 signing wired as an OkHttp interceptor. Every outgoing request to the WalletAssistant backend is automatically signed — verified by a successful HTTP 200 response from a real running backend.

No UI screens. No Jetpack Compose code yet. Foundation only: project structure, HMAC auth, Retrofit networking.

</domain>

<decisions>
## Implementation Decisions

### Package Name
- **D-01:** Application package: `com.dawidcisowski.walletassistant`

### Language Split
- **D-02:** `net/` package in **Java**: `HmacUtil.java` (copy 1:1 from HealthAssistant), `HmacSigningInterceptor.java`, `WalletApiService.java` (Retrofit interface)
- **D-03:** Everything else in **Kotlin**: `MainActivity.kt`, ViewModels, Composables, Application class, build scripts (`build.gradle.kts`)
- **Rationale:** Jetpack Compose requires Kotlin — `@Composable` functions cannot be written in Java. Java is restricted to the `net/` package to stay consistent with the HealthAssistant pattern.

### Build Configuration
- **D-04:** No product flavors — single-flavor app with `debug` and `release` build types only
- **D-05:** All three credentials read from `local.properties` and injected into `BuildConfig`:
  - `backend_url` → `BuildConfig.BACKEND_URL`
  - `hmac_secret` → `BuildConfig.HMAC_SECRET`
  - `device_id` → `BuildConfig.DEVICE_ID`
- **D-06:** `local.properties` in `.gitignore` from the first commit (per AUTH-02)

### HMAC Device ID
- **D-07:** Use `BuildConfig.DEVICE_ID` — NOT `Settings.Secure.ANDROID_ID`. Device ID is a static string configured by the developer, registered as a new entry in the backend's `HMAC_DEVICES_JSON`. HealthAssistant's device ID will NOT work.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Context
- `.planning/ROADMAP.md` — Phase 1 goal, success criteria, and dependency chain
- `.planning/REQUIREMENTS.md` — AUTH-01, AUTH-02 acceptance criteria
- `.planning/PROJECT.md` — HMAC canonical string format, backend module structure, constraints

### HealthAssistant Reference Implementation
- `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/src/main/java/com/example/healthassistantmvp/net/HmacUtil.java` — **Copy this file 1:1**. Canonical HMAC signing logic. Do NOT modify (Base64.NO_WRAP is correct).
- `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/build.gradle` — Reference for `buildConfigField` syntax and OkHttp/Retrofit dependency versions. Note: HealthAssistant uses hardcoded secrets in product flavors; WalletAssistant must read from `local.properties` instead.

### Backend Auth Contract
- `src/main/java/org/dawid/cisowski/walletassistant/security/HmacAuthenticationFilter.java` — Backend's HMAC validation. Canonical string: `METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY`. Tolerance window: 600s. Nonce TTL: 600s.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HmacUtil.java` (HealthAssistant): Copy 1:1 to `com.dawidcisowski.walletassistant.net`. Contains `generateSignature()`, `generateTimestamp()`, `generateNonce()` — all needed by the interceptor.
- HealthAssistant's OkHttp + Retrofit setup: Reference for dependency versions and `OkHttpClient.Builder` configuration (30s timeouts, `HttpLoggingInterceptor` in debug mode).

### Established Patterns
- HMAC signing pattern: interceptor reads the request body bytes, calls `HmacUtil.generateSignature()`, adds four headers (`X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`). Body must be buffered since OkHttp request body can only be read once.
- `local.properties` reading in `build.gradle.kts`: use `java.util.Properties`, load `rootProject.file("local.properties")`, inject via `buildConfigField("String", "KEY", "\"${props["key"]}\"")`

### Integration Points
- Backend endpoint for Phase 1 verification: `GET /v1/expenses` (simplest authenticated endpoint) — should return HTTP 200 with HMAC-signed request, 401/403 without.
- Backend HMAC device registry: `HMAC_DEVICES_JSON` env var on the backend must include the new Android device ID + secret before the app can authenticate.

</code_context>

<specifics>
## Specific Ideas

- Phase 1 success verification: make one real HTTP call to the running local backend (`http://10.0.2.2:8080` for emulator or the LAN IP for physical device) from the app's `onCreate` or a test Activity, and confirm HTTP 200 in Logcat.
- `local.properties` keys (suggested): `backend_url=http://192.168.x.x:8080`, `hmac_secret=<base64-encoded-secret>`, `device_id=wallet-android-device`

</specifics>

<deferred>
## Deferred Ideas

- Production flavor / Cloud Run URL switching — deferred to v2 milestone (per STATE.md)
- Jetpack Compose UI screens — start in Phase 2 (Expenses Screen)
- Room DB / offline caching — explicitly out of scope (PROJECT.md)

</deferred>

---

*Phase: 1-Android Foundation*
*Context gathered: 2026-06-17*
