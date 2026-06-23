# Phase 1: Android Foundation - Pattern Map

**Mapped:** 2026-06-17
**Files analyzed:** 7 new Android files + 1 backend reference
**Analogs found:** 7 / 7

## File Classification

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `net/HmacUtil.java` | utility | transform | `HealthAssistant/.../net/HmacUtil.java` | exact (copy 1:1) |
| `net/HmacSigningInterceptor.java` | middleware | request-response | `HealthAssistant/.../net/BackendClient.java` (per-call signing pattern) | role-match |
| `net/WalletApiService.java` | service | request-response | `HealthAssistant/.../net/BackendApiService.java` (implied by BackendClient usage) | role-match |
| `app/build.gradle.kts` | config | — | `HealthAssistant/app/build.gradle` (Groovy DSL → Kotlin DSL) | role-match |
| `MainActivity.kt` | component | request-response | `HealthAssistant/.../net/BackendClient.java` (verification call pattern) | partial-match |
| `WalletAssistantApp.kt` | config | — | Android Application class pattern | no-analog-in-codebase |
| `AndroidManifest.xml` | config | — | standard Android manifest | no-analog-in-codebase |

---

## Pattern Assignments

### `net/HmacUtil.java` (utility, transform)

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/src/main/java/com/example/healthassistantmvp/net/HmacUtil.java`

**Action:** Copy this file verbatim. Change only the package declaration.

**Package change** (line 1):
```java
// FROM:
package com.example.healthassistantmvp.net;

// TO:
package com.dawidcisowski.walletassistant.net;
```

**Full file content to copy** (lines 1-123 of HealthAssistant HmacUtil.java):
```java
package com.dawidcisowski.walletassistant.net;  // <-- only change

import android.util.Base64;
import android.util.Log;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class HmacUtil {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public static String generateSignature(String method, String path, String timestamp,
            String nonce, String deviceId, String body, String secretBase64) {
        // Canonical string: METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY
        String canonicalString = String.format(Locale.US, "%s\n%s\n%s\n%s\n%s\n%s",
                method.toUpperCase(), path, timestamp, nonce, deviceId, body);
        byte[] secretBytes = Base64.decode(secretBase64, Base64.DEFAULT);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
        byte[] hmacBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(hmacBytes, Base64.NO_WRAP); // NO_WRAP is critical
    }

    public static String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static String generateNonce() {
        return UUID.randomUUID().toString().toLowerCase(Locale.US);
    }
}
```

**Critical details confirmed from source:**
- `Base64.NO_WRAP` on line 92 of analog — `DEFAULT` adds trailing newline breaking signature
- `Base64.DEFAULT` for secret decode (line 81) — not `NO_WRAP`
- Canonical string uses `method.toUpperCase()` (line 61)
- Timestamp format: `"yyyy-MM-dd'T'HH:mm:ss'Z'"` in UTC — matches `Instant.parse()` on backend

---

### `net/HmacSigningInterceptor.java` (middleware, request-response)

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/src/main/java/com/example/healthassistantmvp/net/BackendClient.java` (manual per-call signing, lines 64-118 for client setup; lines 192-280 for signing pattern)

**Improvement over analog:** HealthAssistant manually calls `HmacUtil.generateSignature()` per request method. WalletAssistant wraps this in an OkHttp Application Interceptor so all future Retrofit calls sign automatically.

**Imports pattern:**
```java
package com.dawidcisowski.walletassistant.net;

import com.dawidcisowski.walletassistant.BuildConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
```

**Core interceptor pattern (derived from HealthAssistant signing + OkHttp contract):**
```java
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

        // Path MUST include query string — backend's pathWithQuery() includes it
        // (confirmed from HmacAuthenticationFilter.java lines 132-136)
        String path = original.url().encodedPath();
        String query = original.url().encodedQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }

        String signature = HmacUtil.generateSignature(
                original.method(), path, timestamp, nonce, deviceId, bodyString, secret);

        Request signed = original.newBuilder()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce",     nonce)
                .header("X-Signature", signature)
                .method(original.method(), original.body())  // original body reference unchanged
                .build();

        return chain.proceed(signed);
    }
}
```

**Backend contract verified from** `HmacAuthenticationFilter.java` lines 81-96:
- Required headers: `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`
- Canonical string built at lines 122-129: `method\npathWithQuery\ntimestamp\nnonce\ndeviceId\nbody`
- `pathWithQuery` at lines 132-136: appends `?queryString` when query is present

---

### `net/WalletApiService.java` (service, request-response)

**Analog:** HealthAssistant `BackendClient.java` shows per-call Retrofit invocations (e.g. lines 222-228: `apiService.getDailySummary(deviceId, timestamp, nonce, signature, date)`)

**Key difference:** WalletAssistant passes NO auth headers in the Retrofit interface — the `HmacSigningInterceptor` adds them automatically. Interface is clean of auth concerns.

**Backend requirement verified from** `ExpensesController` (referenced in RESEARCH.md): `GET /v1/expenses` requires both `from` and `to` as mandatory `@RequestParam` query params.

**Interface pattern:**
```java
package com.dawidcisowski.walletassistant.net;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import java.util.List;

public interface WalletApiService {
    // Phase 1: verification endpoint only
    // Both params are REQUIRED — backend uses @RequestParam (required=true by default)
    @GET("v1/expenses")
    Call<List<Object>> getExpenses(
            @Query("from") String from,
            @Query("to")   String to
    );
}
```

---

### `app/build.gradle.kts` (config)

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/app/build.gradle` (Groovy DSL)

**Key difference:** WalletAssistant uses Kotlin DSL (`.kts`) and reads secrets from `local.properties` instead of hardcoding in product flavors. No product flavors — single-flavor with `debug`/`release` build types only.

**SDK versions from analog** (lines 9-15 of HealthAssistant `app/build.gradle`):
- `compileSdk 35`
- `minSdk 26`
- `targetSdk 35`
- `JavaVersion.VERSION_11` for both `sourceCompatibility` and `targetCompatibility`

**local.properties injection pattern (Kotlin DSL adaptation of Groovy analog lines 29-46):**
```kotlin
import java.util.Properties

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

android {
    namespace = "com.dawidcisowski.walletassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dawidcisowski.walletassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_URL", "\"${localProps["backend_url"] ?: ""}\"")
            buildConfigField("String", "HMAC_SECRET", "\"${localProps["hmac_secret"] ?: ""}\"")
            buildConfigField("String", "DEVICE_ID",   "\"${localProps["device_id"] ?: ""}\"")
        }
        release {
            buildConfigField("String", "BACKEND_URL", "\"${localProps["backend_url"] ?: ""}\"")
            buildConfigField("String", "HMAC_SECRET", "\"${localProps["hmac_secret"] ?: ""}\"")
            buildConfigField("String", "DEVICE_ID",   "\"${localProps["device_id"] ?: ""}\"")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}
```

**Dependencies pattern from analog** (lines 74-143 of HealthAssistant `app/build.gradle`, networking-relevant subset):
```kotlin
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    // AndroidX minimum
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

---

### `MainActivity.kt` (component, request-response)

**Analog:** HealthAssistant `BackendClient.java` lines 64-118 (OkHttpClient + Retrofit assembly) and lines 192-279 (call-execute-response pattern). Kotlin coroutines replace raw `new Thread()`.

**OkHttpClient + Retrofit assembly pattern (Kotlin, adapted from analog):**
```kotlin
// Assembled once — in MainActivity or a companion object for Phase 1
private fun buildApi(): WalletApiService {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
    }

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HmacSigningInterceptor())  // Application interceptor — signs before logging
        .addInterceptor(loggingInterceptor)
        .build()

    // Retrofit base URL MUST end with /
    val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_URL + "/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(WalletApiService::class.java)
}
```

**Verification call pattern in onCreate (coroutines replace Thread().start() from analog):**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val api = buildApi()

    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val firstOfMonth = today.withDayOfMonth(1)
            val response = api.getExpenses(
                from = firstOfMonth.toString(),
                to   = today.toString()
            ).execute()
            Log.i("WalletAuth", "Verification OK: HTTP ${response.code()}")
        } catch (e: Exception) {
            Log.e("WalletAuth", "Verification FAILED: ${e.message}")
        }
    }
}
```

**Error handling pattern from analog** (repeated across BackendClient methods, e.g. lines 248-278):
```kotlin
// Check response code, log error body on failure
if (response.isSuccessful) {
    Log.i(TAG, "Success: HTTP ${response.code()}")
} else {
    val errorBody = response.errorBody()?.string() ?: ""
    Log.e(TAG, "Failed HTTP ${response.code()}: $errorBody")
}
```

---

### `AndroidManifest.xml` (config)

**No direct analog in HealthAssistant codebase scanned** — standard Android manifest structure.

**Required elements (from RESEARCH.md pitfall 6 — cleartext HTTP):**
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".WalletAssistantApp"
        android:allowBackup="true"
        android:usesCleartextTraffic="true"   <!-- Required for local HTTP (10.0.2.2:8080) -->
        android:label="@string/app_name"
        android:theme="@style/Theme.WalletAssistant">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

### `WalletAssistantApp.kt` (config)

**No analog needed** — empty Application class, boilerplate only.

```kotlin
package com.dawidcisowski.walletassistant

import android.app.Application

class WalletAssistantApp : Application() {
    // Phase 1: no initialization needed
    // Phase 2+: dependency injection, Timber logging setup
}
```

---

## Shared Patterns

### HMAC Signing Contract
**Source:** `HmacAuthenticationFilter.java` lines 80-136 (backend) + `HmacUtil.java` lines 46-102 (HealthAssistant client)
**Apply to:** `HmacSigningInterceptor.java`

Canonical string (confirmed both sides match):
```
METHOD\nPATH_WITH_QUERY\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY
```

Backend `pathWithQuery` (lines 132-136 of `HmacAuthenticationFilter.java`):
```java
private String pathWithQuery(HttpServletRequest request) {
    return Optional.ofNullable(request.getQueryString())
            .map(query -> request.getRequestURI() + "?" + query)
            .orElseGet(request::getRequestURI);
}
```

Android interceptor must produce identical path construction:
```java
String path = original.url().encodedPath();
String query = original.url().encodedQuery();
if (query != null && !query.isEmpty()) path = path + "?" + query;
```

### BuildConfig Secret Injection
**Source:** `HealthAssistant/app/build.gradle` lines 29-46 (product flavors — adapted to local.properties)
**Apply to:** `app/build.gradle.kts`

Pattern: read `local.properties` via `java.util.Properties`, inject via `buildConfigField("String", ...)`. Secrets never committed. `local.properties` in `.gitignore` from first commit.

### OkHttp Client Assembly
**Source:** `HealthAssistant/.../BackendClient.java` lines 78-118
**Apply to:** `MainActivity.kt`

Timeouts: `connectTimeout(30s)`, `readTimeout(30s)`, `writeTimeout(30s)`.
Logging: `HttpLoggingInterceptor.Level.BODY` in debug only, `NONE` in release.
Ordering: `HmacSigningInterceptor` added before `loggingInterceptor` so signed headers appear in logs.

### Response Error Handling
**Source:** `HealthAssistant/.../BackendClient.java` lines 248-278 (repeated pattern across all methods)
**Apply to:** `MainActivity.kt` verification call

```java
// Check isSuccessful(), log response.code(), read errorBody().string() on failure
if (response.isSuccessful() && response.body() != null) {
    callback.onSuccess(response.body());
} else {
    String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
    Log.e(TAG, String.format("HTTP %d: %s\n%s", response.code(), response.message(), errorBody));
    callback.onFailure(...);
}
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `WalletAssistantApp.kt` | config | — | No Application class in HealthAssistant scanned; standard Android boilerplate |
| `AndroidManifest.xml` | config | — | Standard Android structure; only special element is `usesCleartextTraffic` for local dev |

---

## Key Divergences from HealthAssistant

| Aspect | HealthAssistant Pattern | WalletAssistant Pattern | Why |
|--------|------------------------|------------------------|-----|
| Secret source | Hardcoded in `buildConfigField` inside product flavors | Read from `local.properties` | D-05, D-06: secrets never in git |
| Signing location | Per-call inside each `BackendClient` method | `HmacSigningInterceptor` (once, auto) | Eliminates per-call boilerplate for all future endpoints |
| Device ID source | `Settings.Secure.ANDROID_ID` | `BuildConfig.DEVICE_ID` (static string) | D-07: Android 8+ scopes ANDROID_ID per app, unreliable |
| Async model | `new Thread(() -> { }).start()` | Kotlin coroutines + `Dispatchers.IO` | D-03: all non-`net/` code in Kotlin |
| Build scripts | Groovy DSL (`build.gradle`) | Kotlin DSL (`build.gradle.kts`) | D-03 |
| Product flavors | Yes (`local`, `production`) | No — single flavor, `debug`/`release` build types | D-04 |

---

## Metadata

**Analog search scope:** `/Users/dawidcidowski/AndroidStudioProjects/HealthAssistant/` (primary), `/Users/dawidcidowski/IdeaProjects/WalletAssistant/src/main/java/` (backend contract)
**Files scanned:** 4 (HmacUtil.java, BackendClient.java, app/build.gradle, HmacAuthenticationFilter.java)
**Pattern extraction date:** 2026-06-17
