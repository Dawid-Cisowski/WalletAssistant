# Phase 1: Android Foundation - Research

**Researched:** 2026-06-17
**Domain:** Android project scaffolding, HMAC-SHA256 OkHttp interceptor, Retrofit networking, BuildConfig secret injection
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Application package: `com.dawidcisowski.walletassistant`
- **D-02:** `net/` package in Java: `HmacUtil.java` (copy 1:1 from HealthAssistant), `HmacSigningInterceptor.java`, `WalletApiService.java` (Retrofit interface)
- **D-03:** Everything else in Kotlin: `MainActivity.kt`, ViewModels, Composables, Application class, build scripts (`build.gradle.kts`)
- **D-04:** No product flavors — single-flavor app with `debug` and `release` build types only
- **D-05:** All three credentials read from `local.properties`: `backend_url`, `hmac_secret`, `device_id` → injected into `BuildConfig`
- **D-06:** `local.properties` in `.gitignore` from the first commit
- **D-07:** Use `BuildConfig.DEVICE_ID` — NOT `Settings.Secure.ANDROID_ID`

### Claude's Discretion

- Specific Kotlin/coroutines version to use for the skeleton
- OkHttp logging interceptor level (BODY vs HEADERS)
- `INTERNET` permission placement in AndroidManifest
- Whether `MainActivityViewModel` or a direct coroutine in `onCreate` triggers the verification call

### Deferred Ideas (OUT OF SCOPE)

- Production flavor / Cloud Run URL switching — deferred to v2 milestone
- Jetpack Compose UI screens — start in Phase 2
- Room DB / offline caching — explicitly out of scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | App sends every request to the backend with HMAC-SHA256 signature (X-Device-Id, X-Timestamp, X-Nonce, X-Signature headers) — identical scheme to HealthAssistant | HmacUtil.java (copy 1:1), HmacSigningInterceptor pattern documented below |
| AUTH-02 | HMAC secret and device ID configured through BuildConfig from local.properties (never in git) — local.properties in .gitignore from first commit | buildConfigField pattern from HealthAssistant app/build.gradle documented below |
</phase_requirements>

---

## Summary

Phase 1 creates a new Android project at `~/AndroidStudioProjects/WalletAssistantAndroid/` — a clean walking skeleton with no UI, whose sole deliverable is a signed HMAC request that the WalletAssistant backend accepts with HTTP 200. The research is grounded in the existing HealthAssistant reference implementation (`~/AndroidStudioProjects/HealthAssistant/`), which uses the same HMAC-SHA256 signing scheme in a production context. All library versions are taken directly from HealthAssistant's `app/build.gradle` and `gradle/libs.versions.toml` — no guessing required.

The critical insight differentiating this phase from HealthAssistant is the architecture of signing: HealthAssistant manually builds HMAC headers per-request inside `BackendClient.java`. WalletAssistant must wire signing as an `OkHttp Interceptor` so that all future Retrofit calls are signed automatically without per-call boilerplate. This interceptor pattern requires buffering the request body before reading it, which is a known OkHttp gotcha.

The backend's `GET /v1/expenses` endpoint is the Phase 1 verification target. It requires two mandatory query parameters (`from` and `to` as ISO dates), and the HMAC canonical string must include the full path with query string (`/v1/expenses?from=...&to=...`). The backend's device registry (`HMAC_DEVICES_JSON`) must be updated before any request from the new Android device will succeed.

**Primary recommendation:** Copy `HmacUtil.java` verbatim from HealthAssistant, implement `HmacSigningInterceptor.java` as a new OkHttp Application Interceptor (not Network Interceptor), and verify end-to-end with a single coroutine in `MainActivity.onCreate` that calls `GET /v1/expenses` and logs the HTTP status code to Logcat.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HMAC signature generation | `net/` Java layer (HmacUtil.java) | — | Cryptographic utility; copied 1:1 from reference implementation |
| HTTP request signing | `net/` Java layer (HmacSigningInterceptor) | OkHttp interceptor chain | Interceptor pattern ensures all requests are signed without per-call code |
| Retrofit API interface | `net/` Java layer (WalletApiService.java) | — | Locked decision D-02 |
| Secret injection at build time | `build.gradle.kts` (BuildConfig) | `local.properties` | local.properties is developer-only, never committed |
| Network call dispatch | Kotlin (MainActivityViewModel or onCreate) | — | Kotlin for all non-`net/` code per D-03 |
| Android manifest / permissions | `app/src/main/AndroidManifest.xml` | — | INTERNET permission required for any HTTP call |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android Gradle Plugin | 8.13.0 | Build system | Taken from HealthAssistant `libs.versions.toml` [VERIFIED: reference codebase] |
| Gradle Wrapper | 8.13 | Build runner | Taken from HealthAssistant `gradle-wrapper.properties` [VERIFIED: reference codebase] |
| Kotlin Android | 1.9.20 | Primary language | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| kotlinx-coroutines-android | 1.7.3 | Async HTTP call in verification | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| OkHttp | 4.12.0 | HTTP client | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| okhttp3 logging-interceptor | 4.12.0 | Debug network logging | Same version as OkHttp [VERIFIED: reference codebase] |
| Retrofit | 2.9.0 | Type-safe HTTP interface | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| converter-gson | 2.9.0 | JSON deserialization | Same version as Retrofit [VERIFIED: reference codebase] |
| Gson | 2.10.1 | JSON library | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| compileSdk | 35 | Android API level | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| minSdk | 26 | Minimum Android version | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| targetSdk | 35 | Target Android API | Taken from HealthAssistant `app/build.gradle` [VERIFIED: reference codebase] |
| Java source/target | VERSION_11 | JVM bytecode compatibility | Taken from HealthAssistant `compileOptions` [VERIFIED: reference codebase] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Retrofit 2.9.0 | Ktor | Retrofit is the HealthAssistant standard; Ktor would diverge from reference pattern without benefit |
| Gson 2.10.1 | Moshi / kotlinx.serialization | No advantage for this phase; Gson is already in HealthAssistant |
| Manual interceptor | APIKeyAuth header per-call | Interceptor is what CONTEXT.md requires; per-call is HealthAssistant's legacy pattern |

**Installation (app/build.gradle.kts):**
```kotlin
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

> Note: These are Android/Maven-ecosystem libraries. The npm package legitimacy gate does not apply — these are confirmed via the HealthAssistant production codebase.

---

## Package Legitimacy Audit

Android Gradle dependencies are resolved from Google Maven (`google()`) and Maven Central (`mavenCentral()`), not the npm registry. The npm legitimacy gate is not applicable. All versions below are confirmed from the HealthAssistant reference implementation, a working production Android codebase.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| com.squareup.okhttp3:okhttp:4.12.0 | Maven Central | ~10 yrs | Very high | github.com/square/okhttp | OK | Approved [VERIFIED: reference codebase] |
| com.squareup.okhttp3:logging-interceptor:4.12.0 | Maven Central | ~10 yrs | Very high | github.com/square/okhttp | OK | Approved [VERIFIED: reference codebase] |
| com.squareup.retrofit2:retrofit:2.9.0 | Maven Central | ~10 yrs | Very high | github.com/square/retrofit | OK | Approved [VERIFIED: reference codebase] |
| com.squareup.retrofit2:converter-gson:2.9.0 | Maven Central | ~10 yrs | Very high | github.com/square/retrofit | OK | Approved [VERIFIED: reference codebase] |
| com.google.code.gson:gson:2.10.1 | Maven Central | ~15 yrs | Very high | github.com/google/gson | OK | Approved [VERIFIED: reference codebase] |
| org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3 | Maven Central | ~7 yrs | Very high | github.com/Kotlin/kotlinx.coroutines | OK | Approved [VERIFIED: reference codebase] |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious SUS:** none

---

## Architecture Patterns

### System Architecture Diagram

```
local.properties
  (backend_url, hmac_secret, device_id)
        |
        v
  build.gradle.kts
  (buildConfigField → BuildConfig.kt generated)
        |
        v
  WalletAssistantApp.kt
  (Application class — initializes nothing in Phase 1)
        |
        v
  MainActivity.kt
  (onCreate → CoroutineScope.launch { verificationCall() })
        |
        v
  WalletApiService.java [Retrofit interface]
  (Retrofit + OkHttpClient)
        |
        |-- OkHttpClient
        |     |-- HmacSigningInterceptor.java  (Application Interceptor)
        |     |      reads body, calls HmacUtil.generateSignature()
        |     |      adds X-Device-Id, X-Timestamp, X-Nonce, X-Signature headers
        |     |-- HttpLoggingInterceptor (DEBUG only)
        |
        v
  HTTP GET /v1/expenses?from=...&to=...
        |
        v
  WalletAssistant Backend (localhost:8080 / emulator: 10.0.2.2:8080)
  HmacAuthenticationFilter validates signature
        |
        v
  HTTP 200 (success) or 401 (signing error)
        |
        v
  Logcat: "Verification OK: 200" or "Verification FAILED: 401"
```

### Recommended Project Structure

```
app/
  src/main/
    java/com/dawidcisowski/walletassistant/
      net/                          # Java (locked decision D-02)
        HmacUtil.java               # Copy 1:1 from HealthAssistant
        HmacSigningInterceptor.java # NEW — OkHttp Application Interceptor
        WalletApiService.java       # Retrofit interface (GET /v1/expenses stub)
        dto/
          ExpenseResponse.java      # Minimal DTO for verification response
    kotlin/com/dawidcisowski/walletassistant/
      MainActivity.kt               # Verification call in onCreate
      WalletAssistantApp.kt         # Application class (empty for Phase 1)
    res/
      layout/activity_main.xml      # Minimal layout
    AndroidManifest.xml             # INTERNET permission required
  build.gradle.kts                  # buildConfigField for 3 secrets
local.properties                    # NOT in git — contains backend_url, hmac_secret, device_id
.gitignore                          # local.properties listed here from first commit
```

### Pattern 1: BuildConfig Secret Injection

**What:** Read `local.properties` in `build.gradle.kts` and inject as typed `BuildConfig` fields.
**When to use:** Any secret that must not be committed to git.

```kotlin
// Source: HealthAssistant app/build.gradle (adapted to Kotlin DSL)
// app/build.gradle.kts
import java.util.Properties

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "BACKEND_URL", "\"${localProps["backend_url"] ?: ""}\"")
        buildConfigField("String", "HMAC_SECRET", "\"${localProps["hmac_secret"] ?: ""}\"")
        buildConfigField("String", "DEVICE_ID",   "\"${localProps["device_id"] ?: ""}\"")
    }
}
```

**local.properties content (developer fills in; never committed):**
```
sdk.dir=/Users/<you>/Library/Android/sdk
backend_url=http://10.0.2.2:8080
hmac_secret=<base64-encoded-secret>
device_id=wallet-android-device
```

### Pattern 2: HmacSigningInterceptor as OkHttp Application Interceptor

**What:** OkHttp Application Interceptor that buffers the request body, generates HMAC headers, and rebuilds the request.
**When to use:** Wired once into `OkHttpClient.Builder` — signs every request automatically.

**Critical OkHttp gotcha:** `RequestBody` can only be read once. Use `Buffer` to copy the bytes before consuming:

```java
// Source: Derived from HealthAssistant HmacUtil.java + OkHttp interceptor contract [VERIFIED: reference codebase]
// app/src/main/java/com/dawidcisowski/walletassistant/net/HmacSigningInterceptor.java
package com.dawidcisowski.walletassistant.net;

import com.dawidcisowski.walletassistant.BuildConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

public class HmacSigningInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        String timestamp = HmacUtil.generateTimestamp();
        String nonce     = HmacUtil.generateNonce();
        String deviceId  = BuildConfig.DEVICE_ID;
        String secret    = BuildConfig.HMAC_SECRET;

        // Buffer the body — RequestBody can only be read once
        String bodyString = "";
        RequestBody originalBody = original.body();
        if (originalBody != null) {
            Buffer buffer = new Buffer();
            originalBody.writeTo(buffer);
            bodyString = buffer.readString(StandardCharsets.UTF_8);
        }

        // Path includes query string (backend signs path?query)
        String path = original.url().encodedPath();
        String query = original.url().encodedQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }

        String signature = HmacUtil.generateSignature(
                original.method(),
                path,
                timestamp,
                nonce,
                deviceId,
                bodyString,
                secret
        );

        Request signed = original.newBuilder()
                .header("X-Device-Id",  deviceId)
                .header("X-Timestamp",  timestamp)
                .header("X-Nonce",      nonce)
                .header("X-Signature",  signature)
                .method(original.method(), original.body())
                .build();

        return chain.proceed(signed);
    }
}
```

### Pattern 3: OkHttpClient + Retrofit Assembly (Kotlin)

```kotlin
// Source: Derived from HealthAssistant BackendClient.java (adapted to Kotlin + interceptor) [VERIFIED: reference codebase]
// Assembled in MainActivity or a dedicated NetworkModule object

val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
}

val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .addInterceptor(HmacSigningInterceptor())   // Application Interceptor — signs before logging
    .addInterceptor(loggingInterceptor)
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl(BuildConfig.BACKEND_URL + "/")     // Must end with /
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val api = retrofit.create(WalletApiService::class.java)
```

### Pattern 4: Verification Call in MainActivity

**What:** Minimal smoke test in `onCreate` — calls `GET /v1/expenses` and logs the HTTP code to Logcat.
**When to use:** Phase 1 only; will be replaced by ViewModel architecture in Phase 2.

```kotlin
// Source: Pattern derived from BackendClient usage in HealthAssistant [VERIFIED: reference codebase]
// app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val firstOfMonth = today.withDayOfMonth(1)
            val response = api.getExpenses(
                from = firstOfMonth.toString(),
                to   = today.toString()
            )
            Log.i("WalletAuth", "Verification OK: HTTP ${response.code()}")
        } catch (e: Exception) {
            Log.e("WalletAuth", "Verification FAILED: ${e.message}")
        }
    }
}
```

### Pattern 5: WalletApiService Retrofit Interface

**What:** Minimal Retrofit interface with the one endpoint needed for Phase 1 verification.

```java
// Source: Derived from HealthAssistant BackendApiService.java pattern [VERIFIED: reference codebase]
// app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java
package com.dawidcisowski.walletassistant.net;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import java.util.List;

public interface WalletApiService {
    // GET /v1/expenses?from=...&to=...
    // Both params are REQUIRED by the backend controller
    @GET("v1/expenses")
    Call<List<Object>> getExpenses(
            @Query("from") String from,
            @Query("to")   String to
    );
}
```

> Note: `List<Object>` is sufficient for Phase 1 verification (we only check HTTP status code). Phase 2 will replace with a typed `ExpenseResponse` DTO.

### Anti-Patterns to Avoid

- **Using Network Interceptor instead of Application Interceptor:** Network Interceptors run after redirects and for cached responses; body buffering may behave differently. Use `addInterceptor()` (Application), not `addNetworkInterceptor()`.
- **Hardcoding secrets in build.gradle.kts:** HealthAssistant hardcodes secrets in product flavors — WalletAssistant must NOT do this. Always read from `local.properties`.
- **Using `Settings.Secure.ANDROID_ID` as device ID:** Decision D-07 locks device ID to `BuildConfig.DEVICE_ID` — a static string registered in the backend's `HMAC_DEVICES_JSON`.
- **Signing the path without the query string:** The backend's `pathWithQuery()` includes the query string in the canonical string. A GET to `/v1/expenses?from=2026-01-01&to=2026-06-30` must sign the full path with query, not just `/v1/expenses`.
- **Committing local.properties:** Android Studio auto-generates `sdk.dir` in `local.properties`. The `.gitignore` entry must be present from the first commit, before `local.properties` is touched.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP client | Custom HttpURLConnection wrapper | OkHttp 4.12.0 | Connection pooling, timeouts, interceptor chain, TLS — all handled |
| JSON serialization | Manual `JSONObject` parsing | Gson 2.10.1 + Retrofit converter | Type-safe, handles nulls, date formats |
| HMAC-SHA256 | Custom crypto code | `HmacUtil.java` copied 1:1 from HealthAssistant | Already correct: `Base64.NO_WRAP`, UTC timestamp, UUID nonce |
| Async HTTP calls | `new Thread(() -> { ... }).start()` | Kotlin Coroutines (`Dispatchers.IO`) | HealthAssistant's thread-per-call pattern works but is not idiomatic Kotlin |
| Retrofit base URL trailing slash | String manipulation | Always append `/` to `BuildConfig.BACKEND_URL` in Retrofit builder | Retrofit requires base URL to end with `/`; `http://host:8080/v1/` must be `http://host:8080/` with path `v1/…` in annotation |

**Key insight:** The HMAC signing logic is non-trivial — the canonical string format (`METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY`), the `Base64.NO_WRAP` flag, and the UTC timestamp format are all exact-match requirements with the backend. Copying `HmacUtil.java` verbatim eliminates risk of introducing a subtle mismatch.

---

## Common Pitfalls

### Pitfall 1: New device ID not registered in backend HMAC_DEVICES_JSON

**What goes wrong:** App sends requests with `device_id=wallet-android-device`, backend responds HTTP 401 `Unknown device`.
**Why it happens:** The backend reads device secrets from the `HMAC_DEVICES_JSON` environment variable at startup. HealthAssistant's device ID works for HealthAssistant only.
**How to avoid:** Before running the Phase 1 verification, add a new entry to the backend's `HMAC_DEVICES_JSON` (the env var on the backend process). The format is `{"wallet-android-device": "<base64-secret>"}`. Use a NEW base64-encoded secret (generate with `openssl rand -base64 32`). Set the same secret string in `local.properties` under `hmac_secret`.
**Warning signs:** HTTP 401 with body `{"error":"HMAC_AUTH_FAILED","message":"Unknown device"}`.

### Pitfall 2: GET /v1/expenses requires mandatory query params

**What goes wrong:** Calling `GET /v1/expenses` without `?from=...&to=...` results in HTTP 400 (missing required parameter).
**Why it happens:** The backend controller uses `@RequestParam` (required by default) for both `from` and `to`.
**How to avoid:** Always include both date params. For Phase 1 verification, use current month: `from=<first-of-month>&to=<today>`.
**Warning signs:** HTTP 400 (not 401) — this is a missing-parameter error, not an auth error.

### Pitfall 3: HMAC path must include query string

**What goes wrong:** Signing `/v1/expenses` but sending `GET /v1/expenses?from=2026-01-01&to=2026-06-30` → HTTP 401 `Signature mismatch`.
**Why it happens:** The backend's `pathWithQuery()` includes the query string in the canonical string. If the interceptor signs only the path, the signature will not match.
**How to avoid:** In `HmacSigningInterceptor`, build `path` as `encodedPath + "?" + encodedQuery` when a query string is present (see Pattern 2 above).
**Warning signs:** 401 with `Signature mismatch` on endpoints that have query params, but 200 on endpoints without query params.

### Pitfall 4: RequestBody double-read in OkHttp interceptor

**What goes wrong:** Reading `request.body()` directly in the interceptor exhausts the stream; the actual HTTP request body is empty.
**Why it happens:** OkHttp `RequestBody` is a write-once stream. Reading it in the interceptor consumes it.
**How to avoid:** Copy body bytes into an `okio.Buffer`, read the string from the buffer, then use the original `request.body()` reference unchanged in the rebuilt request (the body object itself is not consumed by the buffer copy — only `writeTo` is called on it, not consumed from it). See Pattern 2 code above.
**Warning signs:** Backend receives empty body on POST requests; HMAC signature computed over empty string does not match.

### Pitfall 5: Retrofit baseUrl trailing slash

**What goes wrong:** `IllegalArgumentException: baseUrl must end with /` or paths are incorrectly concatenated.
**Why it happens:** Retrofit requires the base URL to end with `/`. If `BuildConfig.BACKEND_URL` is `http://10.0.2.2:8080`, Retrofit throws at construction time.
**How to avoid:** Append `/` when constructing Retrofit: `.baseUrl(BuildConfig.BACKEND_URL + "/")` — or add the trailing slash in `local.properties`. The `@GET("v1/expenses")` annotation path must NOT start with `/`.
**Warning signs:** `IllegalArgumentException` at app startup, or 404 because path is double-slash.

### Pitfall 6: Cleartext HTTP blocked on Android 9+

**What goes wrong:** Network call to `http://10.0.2.2:8080` fails silently or throws `java.io.IOException: Cleartext HTTP traffic not permitted`.
**Why it happens:** Android 9+ blocks cleartext (non-HTTPS) traffic by default. Local backend uses HTTP.
**How to avoid:** Add `android:usesCleartextTraffic="true"` to `<application>` in `AndroidManifest.xml`. This is safe for development-only use. Never ship with this flag to production.
**Warning signs:** `CLEARTEXT communication not permitted` in Logcat; no network response despite correct signing.

### Pitfall 7: local.properties already exists with sdk.dir when .gitignore is added

**What goes wrong:** `local.properties` is already tracked by git (Android Studio created it before `.gitignore` was set up).
**Why it happens:** Android Studio writes `sdk.dir=...` to `local.properties` on project open if the file doesn't exist yet.
**How to avoid:** Add `local.properties` to `.gitignore` before opening the project in Android Studio, OR if already created: `git rm --cached local.properties` then commit `.gitignore` + the removal together as the first commit.
**Warning signs:** `git status` shows `local.properties` as tracked; secrets could leak if pushed.

---

## Code Examples

### HmacUtil.java — copy verbatim

Source: `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/src/main/java/com/example/healthassistantmvp/net/HmacUtil.java` [VERIFIED: reference codebase]

Only the package declaration changes: `package com.dawidcisowski.walletassistant.net;`

Key implementation details confirmed from source:
- Canonical string: `String.format(Locale.US, "%s\n%s\n%s\n%s\n%s\n%s", method, path, timestamp, nonce, deviceId, body)`
- Secret decoded with `Base64.decode(secretBase64, Base64.DEFAULT)`
- Signature encoded with `Base64.encodeToString(hmacBytes, Base64.NO_WRAP)` — the `NO_WRAP` flag is critical; `DEFAULT` would add a trailing newline that breaks the signature
- Timestamp format: `"yyyy-MM-dd'T'HH:mm:ss'Z'"` in UTC (matches `Instant.parse()` on the backend)

### HMAC_DEVICES_JSON format for backend

```json
{"wallet-android-device": "<base64-encoded-32-byte-secret>"}
```

Generate secret: `openssl rand -base64 32`

This JSON goes into the backend's `HMAC_DEVICES_JSON` environment variable (update before starting the backend for verification). The same base64 value goes into `local.properties` as `hmac_secret`.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Groovy `build.gradle` | Kotlin `build.gradle.kts` | AGP 7.x+ | WalletAssistant uses `.kts` per D-03; HealthAssistant still uses Groovy |
| `Thread().start()` for async calls | Kotlin Coroutines + `Dispatchers.IO` | Kotlin 1.3+ | Idiomatic Kotlin; HealthAssistant uses raw threads (legacy) |
| `Settings.Secure.ANDROID_ID` as device ID | Static string from `BuildConfig.DEVICE_ID` | Android 8 scoped IDs | Per-app scoped in Android 8+; unreliable as a stable device identifier |

**Deprecated/outdated:**
- `ANDROID_ID` as device identifier: scoped per app since Android 8+ — not suitable as a stable registered device ID

---

## Runtime State Inventory

> Not applicable — this is a greenfield phase creating a new project. There is no existing runtime state to migrate.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android Studio | Project creation | Yes | Found at `/Applications/Android Studio.app` | — |
| Android SDK (API 35) | compileSdk 35 | Yes | `~/Library/Android/sdk/platforms/android-35` | — |
| Android SDK (API 36) | Bonus | Yes | `~/Library/Android/sdk/platforms/android-36` | — |
| ADB | Emulator/device testing | Yes | Found at `~/Library/Android/sdk/platform-tools/adb` | — |
| Java 21 | Gradle build tool | Yes | `openjdk version "21.0.1"` at `/usr/bin/java` | — |
| WalletAssistant backend (localhost:8080) | Phase 1 verification HTTP 200 | NOT running at research time | — | Must be started manually before verification |
| `~/AndroidStudioProjects/WalletAssistantAndroid/` | Target project directory | Does not exist yet | — | Will be created by Android Studio wizard |

**Missing dependencies with no fallback:**
- WalletAssistant backend must be running locally at verification time. Start with `cd ~/IdeaProjects/WalletAssistant && ./gradlew bootRun` (requires `docker-compose up -d` for PostgreSQL first).

**Missing dependencies with fallback:** none

---

## Security Domain

`security_enforcement: true`, ASVS level 1.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Yes | HMAC-SHA256 with per-device secret — covers machine-to-machine auth |
| V3 Session Management | No | No user sessions; per-request HMAC signature is stateless |
| V4 Access Control | No | Single-user personal app; backend enforces device-level access |
| V5 Input Validation | Minimal | `local.properties` values are developer-supplied at build time; no user input in Phase 1 |
| V6 Cryptography | Yes | HmacUtil uses `HmacSHA256` via `javax.crypto.Mac`; secret decoded from Base64; `Base64.NO_WRAP` for output |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret leakage via git | Information disclosure | `local.properties` in `.gitignore` from first commit (AUTH-02) |
| Nonce replay | Spoofing | Backend Caffeine nonce cache (600s TTL, 10k entries) — interceptor generates fresh UUID per request |
| Timestamp replay | Spoofing | Backend 600s tolerance window — interceptor generates current UTC timestamp per request |
| Cleartext secret in BuildConfig | Information disclosure | `BuildConfig` fields are compiled into the APK; for personal use this is acceptable. For distribution, use Android Keystore. |
| Hardcoded secret in source | Information disclosure | Reading from `local.properties` prevents this; `local.properties` is never committed |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `compileSdk 36` (available in SDK) can be used if developer prefers latest, but 35 is sufficient and confirmed | Standard Stack | Low — both are available on this machine |
| A2 | `http://10.0.2.2:8080` is the emulator loopback for Android emulator to reach host; physical device needs LAN IP | Architecture Patterns | Medium — developer must substitute correct IP for physical device |
| A3 | HealthAssistant AGP 8.13.0 + Gradle 8.13 are compatible with Kotlin DSL build scripts | Standard Stack | Low — Gradle 8+ has first-class Kotlin DSL support |

---

## Open Questions

1. **Backend HMAC_DEVICES_JSON update procedure**
   - What we know: the backend reads `HMAC_DEVICES_JSON` env var at startup; the format is a JSON object mapping deviceId to base64 secret.
   - What's unclear: whether there is an existing `.env` file or `application-local.yml` that should be updated, or whether the developer sets the env var manually.
   - Recommendation: The plan should include a task to update the backend's local dev configuration with the new device entry and restart the backend before attempting Phase 1 verification.

2. **Emulator vs physical device**
   - What we know: emulator uses `10.0.2.2` to reach host; physical device needs LAN IP.
   - What's unclear: which device the developer will use for verification.
   - Recommendation: Plan task should instruct setting `backend_url` in `local.properties` for the chosen device type, defaulting to emulator (`http://10.0.2.2:8080`).

---

## Sources

### Primary (HIGH confidence)
- `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/src/main/java/com/example/healthassistantmvp/net/HmacUtil.java` — canonical HMAC signing logic, confirmed correct
- `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/build.gradle` — all library versions, BuildConfig field syntax, OkHttp/Retrofit dependency declarations
- `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/gradle/libs.versions.toml` — AGP 8.13.0
- `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/gradle/wrapper/gradle-wrapper.properties` — Gradle 8.13
- `/Users/dawidcidowski/IdeaProjects/WalletAssistant/src/main/java/org/dawid/cisowski/walletassistant/security/HmacAuthenticationFilter.java` — backend HMAC contract: canonical string format, pathWithQuery logic, 600s tolerance
- `/Users/dawidcidowski/IdeaProjects/WalletAssistant/src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpensesController.java` — GET /v1/expenses requires `from` and `to` query params (both required)
- `/Users/dawidcidowski/IdeaProjects/WalletAssistant/src/main/java/org/dawid/cisowski/walletassistant/config/AppProperties.java` — HMAC_DEVICES_JSON format: JSON map of deviceId → base64 secret

### Secondary (MEDIUM confidence)
- `.planning/phases/01-android-foundation/01-CONTEXT.md` — locked decisions D-01 through D-07, phase boundary, code patterns
- `.planning/REQUIREMENTS.md` — AUTH-01, AUTH-02 acceptance criteria
- `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/src/main/java/com/example/healthassistantmvp/net/BackendClient.java` — OkHttpClient assembly pattern, timeout values, logging interceptor usage

### Tertiary (LOW confidence)
- Training knowledge on OkHttp interceptor body buffering pattern using `okio.Buffer`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions taken directly from HealthAssistant production codebase
- Architecture: HIGH — backend auth contract read from source; interceptor pattern derived from HealthAssistant with documented body-buffering gotcha
- Pitfalls: HIGH — all pitfalls derived from actual source code (backend filter logic, controller required params, Android cleartext policy)

**Research date:** 2026-06-17
**Valid until:** 2026-09-17 (stable libraries; AGP versions may update but pattern is stable)
