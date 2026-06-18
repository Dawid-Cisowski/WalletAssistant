# Phase 2: Expenses Screen - Pattern Map

**Mapped:** 2026-06-18
**Files analyzed:** 11 new/modified files
**Analogs found:** 7 / 11 (4 are greenfield with no existing Android analog — see No Analog Found section)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `app/build.gradle.kts` | config | — | `app/build.gradle.kts` (self — modify) | exact |
| `build.gradle.kts` (root) | config | — | `build.gradle.kts` (self — modify) | exact |
| `WalletAssistantApp.kt` | config | — | `WalletAssistantApp.kt` (self — annotate) | exact |
| `MainActivity.kt` | component | request-response | `MainActivity.kt` (self — rewrite) | exact |
| `di/NetworkModule.kt` | config | request-response | `net/NetworkModule.kt` (refactor source) | exact |
| `net/WalletApiService.kt` | service | request-response | `net/WalletApiService.java` (migrate source) | exact |
| `expenses/ExpenseDto.kt` | model | transform | `src/main/java/.../expenses/api/ExpenseResponse.java` | role-match |
| `expenses/ExpenseCategory.kt` | model | — | `src/main/java/.../expenses/ExpenseCategory.java` | role-match |
| `expenses/ExpensesRepository.kt` | service | request-response | `net/NetworkModule.kt` (data-access layer) | partial |
| `expenses/ExpensesViewModel.kt` | component | request-response | — (greenfield) | none |
| `expenses/ui/ExpensesScreen.kt` | component | request-response | — (greenfield) | none |
| `expenses/ui/ExpenseListItem.kt` | component | — | — (greenfield) | none |
| `expenses/ui/DateRangePickerModal.kt` | component | event-driven | — (greenfield) | none |

---

## Pattern Assignments

### `build.gradle.kts` (root) — add Hilt + KSP plugins

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/build.gradle.kts`

**Current state** (lines 1–5):
```kotlin
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
```

**Required additions — add two lines to `plugins {}` block:**
```kotlin
id("com.google.dagger.hilt.android") version "2.57.1" apply false
id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
```

---

### `app/build.gradle.kts` — add Compose + Hilt + KSP, upgrade Java to 17

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/build.gradle.kts`

**Plugins block** (lines 10–13) — add two plugins:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")   // ADD
    id("com.google.devtools.ksp")           // ADD
}
```

**android block** — add compose support and upgrade Java:
```kotlin
buildFeatures {
    buildConfig = true
    compose = true   // ADD
}

composeOptions {
    kotlinCompilerExtensionVersion = "1.5.5"  // ADD — locked to Kotlin 1.9.20
}

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17  // UPGRADE from VERSION_11
    targetCompatibility = JavaVersion.VERSION_17  // UPGRADE from VERSION_11
}

kotlinOptions {
    jvmTarget = "17"  // UPGRADE from "11"
}
```

**dependencies block** — full set of additions (append after existing):
```kotlin
// Compose BOM — controls all androidx.compose.* versions (locked to Kotlin 1.9.20)
val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
implementation(composeBom)

// Compose core
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.activity:activity-compose:1.9.0")

// Lifecycle + ViewModel for Compose
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.57.1")
ksp("com.google.dagger:hilt-compiler:2.57.1")

// Hilt + Compose navigation integration (provides hiltViewModel())
implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

// Debug-only tooling
debugImplementation("androidx.compose.ui:ui-tooling")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

**Keep unchanged:** All existing retrofit/okhttp/gson/appcompat/lifecycle-runtime-ktx entries.

---

### `WalletAssistantApp.kt` — add `@HiltAndroidApp`

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/WalletAssistantApp.kt`

**Current file** (lines 1–8):
```kotlin
package com.dawidcisowski.walletassistant

import android.app.Application

class WalletAssistantApp : Application() {
    // Phase 1: no initialization needed
    // Phase 2+: dependency injection, Timber logging setup
}
```

**Required change — add annotation and import:**
```kotlin
package com.dawidcisowski.walletassistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WalletAssistantApp : Application()
```

Also ensure `AndroidManifest.xml` has `android:name=".WalletAssistantApp"` on the `<application>` tag (Phase 1 likely set this already; verify).

---

### `MainActivity.kt` — rewrite with Compose `setContent {}`

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt`

**Current file** (lines 1–39) — walking skeleton using AppCompatActivity + lifecycleScope. Phase 2 replaces entirely.

**Required pattern:**
```kotlin
package com.dawidcisowski.walletassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.dawidcisowski.walletassistant.expenses.ui.ExpensesScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Material3 theme wrapper (create AppTheme or use MaterialTheme directly)
            ExpensesScreen()
        }
    }
}
```

Note: `ComponentActivity` (not `AppCompatActivity`) is the required base for `setContent {}` with Compose.

---

### `di/NetworkModule.kt` — refactor object to Hilt `@Module`

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/net/NetworkModule.kt`

**Current file** (lines 20–52) — Kotlin `object NetworkModule` with a `walletApi(context: Context)` factory method. Must be replaced with a Hilt `@Module`.

**Core pattern from existing analog** (lines 22–51 — extract and convert):
```kotlin
package com.dawidcisowski.walletassistant.di

import com.dawidcisowski.walletassistant.BuildConfig
import com.dawidcisowski.walletassistant.net.HmacSigningInterceptor
import com.dawidcisowski.walletassistant.net.WalletApiService
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson() = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ ->
            LocalDate.parse(json.asString)
        })
        .create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Device ID from BuildConfig — matches what HmacSigningInterceptor uses
        val deviceId = BuildConfig.DEVICE_ID
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HmacSigningInterceptor(deviceId))  // HMAC first — visible in debug logs
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideWalletApiService(okHttpClient: OkHttpClient, gson: com.google.gson.Gson): WalletApiService {
        val rawUrl = BuildConfig.BACKEND_URL.trimEnd('/')
        return Retrofit.Builder()
            .baseUrl("$rawUrl/")  // trailing slash — Retrofit contract
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WalletApiService::class.java)
    }
}
```

**Delete `net/NetworkModule.kt`** after creating `di/NetworkModule.kt` — keeping both causes duplicate Hilt component error (RESEARCH.md Pitfall 8).

---

### `net/WalletApiService.kt` — migrate from Java Call<> to Kotlin suspend fun

**Analog:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java`

**Current file** (lines 18–37) — Java interface with `Call<List<Object>>`. Must become a Kotlin interface with `suspend fun` returning typed list.

**Required pattern — replace the .java file entirely with .kt:**
```kotlin
package com.dawidcisowski.walletassistant.net

import com.dawidcisowski.walletassistant.expenses.ExpenseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for WalletAssistant backend API.
 *
 * No auth headers declared here — HmacSigningInterceptor adds them to every request.
 * Path MUST NOT start with "/" — Retrofit base URL trailing-slash contract.
 */
interface WalletApiService {

    /**
     * GET /v1/expenses?from=YYYY-MM-DD&to=YYYY-MM-DD
     * Both params are required — backend uses @RequestParam(required=true).
     */
    @GET("v1/expenses")
    suspend fun getExpenses(
        @Query("from") from: String,
        @Query("to") to: String
    ): List<ExpenseDto>
}
```

`HmacSigningInterceptor.java` and `HmacUtil.java` stay Java — no changes needed.

---

### `expenses/ExpenseDto.kt` — Kotlin data class mirroring backend ExpenseResponse

**Analog:** `/Users/dawidcidowski/IdeaProjects/WalletAssistant/src/main/java/org/dawid/cisowski/walletassistant/expenses/api/ExpenseResponse.java`

**Backend source** (lines 7–19) — Java record with `BigDecimal amount`, `Instant occurredAt`, `LocalDate occurredDate`.

**Required Android mirror:**
```kotlin
package com.dawidcisowski.walletassistant.expenses

data class ExpenseDto(
    val expenseId: String,
    val eventId: String,
    val amount: Double,         // BigDecimal → Double (Gson-safe; Phase 4 upgrades this)
    val currency: String,
    val category: String,       // Raw string — mapped to ExpenseCategory enum in ViewModel
    val description: String,
    val merchant: String?,      // Nullable — omit from list item display when null/blank (D-11)
    val accountType: String,
    val occurredAt: String,     // Instant as ISO string — not displayed directly in Phase 2
    val occurredDate: String    // LocalDate as "2026-06-18" — parsed to LocalDate in ViewModel
)
```

**Why `amount` is `Double`:** Gson has no BigDecimal type adapter; Double is sufficient for Phase 2 display and summation. Phase 4 addresses currency formatting precision.
**Why `occurredDate` is `String`:** Custom `JsonDeserializer<LocalDate>` is registered in `NetworkModule` for Gson, so `LocalDate` can actually be used here — either approach is valid. Using `LocalDate` directly is cleaner if the deserializer is registered before Retrofit is built.

---

### `expenses/ExpenseCategory.kt` — Kotlin enum with Polish displayName

**Analog:** `/Users/dawidcidowski/IdeaProjects/WalletAssistant/src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpenseCategory.java`

**Backend source** (lines 5–38) — Java enum with 13 values and `displayName()` method.

**Required Android mirror:**
```kotlin
package com.dawidcisowski.walletassistant.expenses

enum class ExpenseCategory(val displayName: String) {
    FOOD_AND_DRINKS("Jedzenie i napoje"),
    TRANSPORT("Transport"),
    SHOPPING("Zakupy"),
    ENTERTAINMENT("Rozrywka"),
    SUBSCRIPTIONS("Subskrypcje"),
    HEALTH("Zdrowie"),
    HOUSING("Mieszkanie"),
    UTILITIES("Media"),
    EDUCATION("Edukacja"),
    TRAVEL("Podróże"),
    BUSINESS("Firmowe"),
    SAVINGS_TRANSFER("Transfer na oszczędności"),
    OTHER("Inne");

    companion object {
        fun fromString(value: String): ExpenseCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: OTHER  // graceful fallback for unknown categories from backend
    }
}
```

13 categories — includes `SAVINGS_TRANSFER` per D-07.

---

### `expenses/ExpensesRepository.kt` — @Singleton wrapping WalletApiService

**Analog:** Pattern derived from `net/NetworkModule.kt` (existing data-access pattern) — no direct Repository analog exists in the Android project yet.

**Required pattern:**
```kotlin
package com.dawidcisowski.walletassistant.expenses

import com.dawidcisowski.walletassistant.net.WalletApiService
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpensesRepository @Inject constructor(
    private val api: WalletApiService
) {

    suspend fun getExpenses(from: LocalDate, to: LocalDate): List<ExpenseDto> =
        api.getExpenses(from.toString(), to.toString())
}
```

Repository returns the raw list. ViewModel owns filtering, sorting, and total computation (D-09, D-14).

---

## No Analog Found (Greenfield files — use RESEARCH.md patterns directly)

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `expenses/ExpensesViewModel.kt` | component (ViewModel) | request-response | No ViewModel exists in the Android project yet |
| `expenses/ui/ExpensesScreen.kt` | component (Composable) | request-response | No Compose screens exist yet |
| `expenses/ui/ExpenseListItem.kt` | component (Composable) | — | No Compose item components exist yet |
| `expenses/ui/DateRangePickerModal.kt` | component (Composable) | event-driven | No date picker or modal components exist yet |

For these files, use RESEARCH.md Pattern 2 (ViewModel + StateFlow), Pattern 3 (Composable collecting from ViewModel), and Pattern 5 (DateRangePicker in DatePickerDialog) directly. All patterns are included verbatim in RESEARCH.md with complete code.

**Key ViewModel state types needed alongside ExpensesViewModel.kt:**

```kotlin
// FilterState.kt — co-locate with ExpensesViewModel or in expenses/ package
data class FilterState(
    val dateRange: Pair<LocalDate, LocalDate>,
    val selectedCategories: Set<ExpenseCategory>,
    val activeDateChip: DateChip
) {
    companion object {
        fun default(): FilterState {
            val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
            return FilterState(
                dateRange = today.withDayOfMonth(1) to today,
                selectedCategories = emptySet(),
                activeDateChip = DateChip.THIS_MONTH
            )
        }
    }
}

enum class DateChip { THIS_MONTH, PREVIOUS_MONTH, CUSTOM }

// ExpensesUiState.kt — co-locate with ExpensesViewModel
sealed class ExpensesUiState {
    object Loading : ExpensesUiState()
    data class Success(
        val expenses: List<ExpenseDto>,   // filtered + sorted list
        val total: Double                  // sum of filtered amounts
    ) : ExpensesUiState()
    data class Error(val message: String) : ExpensesUiState()
}
```

---

## Shared Patterns

### HMAC Authentication (unchanged from Phase 1)
**Source:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/HmacSigningInterceptor.java`
**Apply to:** `di/NetworkModule.kt` (inject into OkHttpClient via `addInterceptor()`)

Constructor takes `deviceId: String` — pass `BuildConfig.DEVICE_ID` (not `ANDROID_ID` — see RESEARCH.md anti-pattern note):
```java
.addInterceptor(HmacSigningInterceptor(deviceId))
```

### Gson LocalDate Deserializer
**Source:** RESEARCH.md Pattern 4 (no existing analog — register in NetworkModule)
**Apply to:** `di/NetworkModule.kt` `provideGson()` method

```kotlin
GsonBuilder()
    .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ ->
        LocalDate.parse(json.asString)
    })
    .create()
```

### BuildConfig Secrets Pattern
**Source:** `/Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/build.gradle.kts` (lines 36–44)
**Apply to:** `di/NetworkModule.kt`

Existing pattern injects `BACKEND_URL`, `HMAC_SECRET`, `DEVICE_ID` from `local.properties` into BuildConfig. No new secrets needed for Phase 2. Access via `BuildConfig.DEVICE_ID`, `BuildConfig.BACKEND_URL`, `BuildConfig.HMAC_SECRET`.

### `@OptIn(ExperimentalMaterial3Api::class)` Guard
**Apply to:** `DateRangePickerModal.kt` and any file that calls `DateRangePicker`, `rememberDateRangePickerState`, or `DatePickerDialog`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(...) { ... }
```

### Warsaw Timezone for LocalDate boundaries
**Apply to:** `FilterState.default()` in ViewModel init, and epoch-millis → LocalDate conversion in `DateRangePickerModal`

```kotlin
val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
// Also for epoch millis from DateRangePicker:
Instant.ofEpochMilli(millis).atZone(ZoneId.of("Europe/Warsaw")).toLocalDate()
```

---

## Metadata

**Analog search scope:** `~/AndroidStudioProjects/WalletAssistantAndroid/` (Android project) + `~/IdeaProjects/WalletAssistant/src/main/java/.../expenses/` (backend for DTO/enum mirrors)
**Files scanned:** 8
**Pattern extraction date:** 2026-06-18
