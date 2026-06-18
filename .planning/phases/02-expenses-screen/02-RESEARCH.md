# Phase 2: Expenses Screen - Research

**Researched:** 2026-06-18
**Domain:** Android / Jetpack Compose / Hilt / MVVM / Material3
**Confidence:** MEDIUM

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Dependency injection via **Hilt** — `@HiltViewModel` on `ExpensesViewModel`, `hiltViewModel()` in Composable. App class annotated `@HiltAndroidApp`. This pattern is the template for Phase 3 and 4 screens.
- **D-02:** UI state modeled as **StateFlow + sealed class**: `sealed class ExpensesUiState { Loading, Success(expenses: List<Expense>, total: BigDecimal), Error(message: String) }`. Collected in Composable via `collectAsState()`.
- **D-03:** **Repository layer** — `ExpensesRepository` wraps `WalletApiService` calls. ViewModel injects repository, not the API interface directly.
- **D-04:** Quick-access filter chips: **"Ten miesiąc"** / **"Poprzedni"** / **"Niestandardowy"**. Tapping "Niestandardowy" opens a Material3 `DateRangePicker` in a modal.
- **D-05:** Active date range displayed as compact text in a chip/label above the expense list.
- **D-06:** Default range = current calendar month (first day of month → today). Determined at ViewModel init.
- **D-07:** Show **all 13 categories** including `SAVINGS_TRANSFER`.
- **D-08:** Layout: **horizontal `LazyRow` of `FilterChip`** components. Single scrollable row.
- **D-09:** Selection logic: **no chip selected = show all expenses**. Filtering is **client-side**.
- **D-10:** Display names use Polish labels from `ExpenseCategory.displayName()`.
- **D-11:** Expense list item two-row layout: description + amount (row 1), merchant/category + date (row 2).
- **D-12:** Category display: Polish text label only. No color indicators or icons.
- **D-13:** Amount format: raw number + currency string (e.g., "123.45 PLN"). Polish formatting deferred to Phase 4.
- **D-14:** Total is **always calculated client-side** from the currently displayed expense list. No call to `/v1/expenses/summary/monthly`.

### Claude's Discretion

Not documented separately in CONTEXT.md — all significant UI decisions are locked in D-01 through D-14 and the UI-SPEC.

### Deferred Ideas (OUT OF SCOPE)

- Polish currency formatting ("1 234,56 zł") — Phase 4 scope (APP-04)
- Expense item color coding by category
- Charts / analytics (CHART-01 through CHART-04) — v2
- Dark mode (UX-01) — v2
- Cloud Run deploy (INFRA-01 through INFRA-04) — v2
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EXP-01 | Użytkownik widzi listę wydatków w odwrotnej kolejności chronologicznej z domyślnym bieżącym miesiącem | MVVM + StateFlow pattern; ViewModel initialises date range; LazyColumn sorted by occurredDate desc |
| EXP-02 | Użytkownik może wybrać zakres dat (date range picker) | Material3 DateRangePicker + DatePickerDialog; quick chips for current/previous month |
| EXP-03 | Użytkownik może filtrować wydatki według kategorii (chip filtry) | FilterChip in LazyRow; client-side filter in ViewModel; all 13 categories shown |
| EXP-04 | Użytkownik widzi kartę z łączną sumą wydatków dla wybranego okresu | Summary Card component; total computed client-side from filtered list |
</phase_requirements>

---

## Summary

Phase 2 builds the Expenses Screen in the existing Android project at `~/AndroidStudioProjects/WalletAssistantAndroid/`. The phase has two distinct layers of work: (1) infrastructure upgrade — adding Hilt DI, Jetpack Compose, and the KSP annotation processor to the existing build; and (2) feature implementation — the MVVM Expenses Screen with date range selection, category filter chips, and a summary card.

The existing project uses Kotlin 1.9.20 with Android Gradle Plugin 8.13.0 and Gradle 8.13. This Kotlin version constrains the Compose compiler extension to 1.5.5 and the compatible Compose BOM to the 2024.09.xx range (Material3 1.3.x, Compose UI 1.7.x). This is a hard compatibility constraint — the project must NOT use the latest BOM (2026.06.00 with Material3 1.4.0) because it requires Kotlin 2.0+. [VERIFIED: developer.android.com/jetpack/androidx/releases/compose-kotlin]

The current `NetworkModule.kt` is an `object` (singleton Kotlin object, not a Hilt module). It passes `Context` per call to read `ANDROID_ID`. Phase 2 must refactor it to a proper Hilt `@Module` so the `WalletApiService` is injected as a `@Singleton`. The `WalletApiService.java` interface must also be upgraded from `Call<List<Object>>` to `suspend fun getExpenses(): List<ExpenseDto>`.

**Primary recommendation:** Add KSP + Hilt plugins to the build, then wire `@Module`, `@HiltAndroidApp`, `@AndroidEntryPoint`, and `@HiltViewModel` before writing any Compose UI — the DI infrastructure must compile before screens can reference the ViewModel.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Fetch expenses list from backend | ViewModel / Repository | — | Network call triggered by ViewModel; Repository abstracts Retrofit |
| Date range state management | ViewModel | — | D-06: default range computed at ViewModel init, not in Composable |
| Category filter state | ViewModel | — | D-09: client-side filter; filtered list derived in ViewModel |
| Total spend calculation | ViewModel | — | D-14: sum computed from filtered list inside ViewModel state |
| Date range picker UI | Composable | ViewModel (state) | UI component opened from Composable; confirmed selection sent to ViewModel |
| Expense list rendering | Composable | — | LazyColumn is pure UI; data supplied by ViewModel via StateFlow |
| HTTP authentication (HMAC) | OkHttp Interceptor | — | HmacSigningInterceptor already complete from Phase 1 |
| Gson deserialization | Retrofit converter | — | Custom LocalDate deserializer registered in GsonBuilder |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.dagger:hilt-android` | 2.57.1 | Dependency injection framework | Official Android DI solution, Compose-native via `hiltViewModel()` |
| `com.google.dagger:hilt-compiler` | 2.57.1 | KSP annotation processor for Hilt | Generates Hilt component code at compile time |
| `com.google.devtools.ksp` plugin | 1.9.20-1.0.14 | Kotlin Symbol Processing (replaces kapt) | 2x faster than kapt; official recommendation for new projects [CITED: developer.android.com/build/migrate-to-ksp] |
| `androidx.compose:compose-bom` | **2024.09.00** | Compose Bill of Materials | Locks all Compose library versions to compatible set for Kotlin 1.9.20 [VERIFIED: developer.android.com/develop/ui/compose/bom/bom-mapping] |
| `androidx.compose.material3:material3` | 1.3.0 (via BOM) | Material3 components (FilterChip, Card, etc.) | Driven by BOM 2024.09.00 constraint |
| `androidx.compose.ui:ui` | 1.7.x (via BOM) | Core Compose UI primitives | Driven by BOM |
| `androidx.compose.ui:ui-tooling-preview` | (via BOM) | Android Studio preview | Required for @Preview annotations |
| `androidx.activity:activity-compose` | 1.9.x | `setContent {}` and `ComponentActivity` | Required to launch Compose from Activity |
| `androidx.hilt:hilt-navigation-compose` | 1.3.0 | `hiltViewModel()` factory in Composable | Official Hilt-Compose integration [CITED: developer.android.com/training/dependency-injection/hilt-jetpack] |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.x | ViewModel integration in Compose | Standard ViewModel access in Composable |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.x | `collectAsStateWithLifecycle()` | Lifecycle-aware StateFlow collection — preferred over `collectAsState()` [CITED: developer.android.com/topic/libraries/architecture/lifecycle] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.compose.material:material-icons-extended` | (via BOM) | Additional Material icons | Required for `Icons.Default.ReceiptLong` (empty state) and `Icons.Default.ErrorOutline` (error state) per UI-SPEC |
| `androidx.compose.ui:ui-tooling` | (via BOM) | Debug-only preview tooling | `debugImplementation` only |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hilt | Koin | Hilt is locked per D-01; Koin would eliminate KSP but break the template pattern |
| `collectAsStateWithLifecycle` | `collectAsState()` | `collectAsState` doesn't stop collection in background — wastes resources. Use `collectAsStateWithLifecycle` always |
| KSP | KAPT | KAPT still works with Kotlin 1.9.20 but is ~2x slower; KSP is the forward path |
| `DatePickerDialog` | `ModalBottomSheet` | UI-SPEC mentions ModalBottomSheet but official Material3 DateRangePicker is designed for `DatePickerDialog`; bottom sheet wrapping requires significant manual sizing work |

### Installation — Root `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}
```

### Installation — App `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    // ... existing config unchanged ...

    buildFeatures {
        buildConfig = true
        compose = true  // ADD THIS
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"  // ADD THIS — locked to Kotlin 1.9.20
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17  // UPGRADE from VERSION_11
        targetCompatibility = JavaVersion.VERSION_17  // UPGRADE from VERSION_11
    }

    kotlinOptions {
        jvmTarget = "17"  // UPGRADE from "11"
    }
}

dependencies {
    // Compose BOM — controls all androidx.compose.* versions
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

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

    // Hilt + Compose navigation integration
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Debug-only tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Existing (keep unchanged)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    // ...
}
```

---

## Package Legitimacy Audit

> All packages in this phase are official Google/JetBrains artifacts distributed via `maven.google.com` and `mavenCentral()`. No npm registry is involved. Package legitimacy protocol (`npm view`) is not applicable for Android Maven artifacts.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `com.google.dagger:hilt-android` | maven.google.com | 5+ yrs | Hundreds of millions | github.com/google/dagger | OK | Approved |
| `com.google.devtools.ksp` | maven.google.com | 4+ yrs | Hundreds of millions | github.com/google/ksp | OK | Approved |
| `androidx.compose:compose-bom` | maven.google.com | 3+ yrs | Hundreds of millions | github.com/androidx/androidx | OK | Approved |
| `androidx.compose.material3:material3` | maven.google.com | 3+ yrs | Hundreds of millions | github.com/androidx/androidx | OK | Approved |
| `androidx.hilt:hilt-navigation-compose` | maven.google.com | 3+ yrs | Tens of millions | github.com/androidx/androidx | OK | Approved |
| `androidx.lifecycle:lifecycle-runtime-compose` | maven.google.com | 3+ yrs | Tens of millions | github.com/androidx/androidx | OK | Approved |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious:** none

---

## Architecture Patterns

### System Architecture Diagram

```
User Interaction
      │
      ▼
ExpensesScreen (Composable — stateless)
  ├── reads: StateFlow<ExpensesUiState>  ──────────────────────────────┐
  ├── reads: StateFlow<FilterState>                                     │
  │         (dateRange, selectedCategories, activeDateChip)            │
  │                                                                     │
  ├── DateQuickChips ──► onDateChipSelected(chip) ──────────────────► ExpensesViewModel
  ├── DateRangePicker ──► onDateRangeConfirmed(start, end) ──────────► (@HiltViewModel)
  ├── CategoryFilterRow ──► onCategoryToggled(category) ────────────►    │
  └── "Spróbuj ponownie" ──► viewModel.retry() ──────────────────────►    │
                                                                          │
                                                    loadExpenses(from, to)
                                                          │
                                                          ▼
                                               ExpensesRepository
                                               (@Singleton, injected)
                                                          │
                                                  suspend getExpenses()
                                                          │
                                                          ▼
                                               WalletApiService (Retrofit)
                                               HmacSigningInterceptor (OkHttp)
                                                          │
                                              GET /v1/expenses?from=&to=
                                                          │
                                                          ▼
                                               WalletAssistant Backend
                                               (returns List<ExpenseDto>)
                                                          │
                                       ◄─── raw List<ExpenseDto> ──────
                                       │
                          client-side filter (by selectedCategories)
                          client-side sort (occurredDate desc, occurredAt desc)
                          client-side total (sum of filtered amounts)
                                       │
                                       ▼
                          emit ExpensesUiState.Success(filteredList, total)
```

### Recommended Project Structure

```
app/src/main/
├── kotlin/com/dawidcisowski/walletassistant/
│   ├── WalletAssistantApp.kt          # @HiltAndroidApp (modify existing)
│   ├── MainActivity.kt                # @AndroidEntryPoint + setContent (rewrite)
│   ├── net/
│   │   └── NetworkModule.kt           # Refactor: object → @Module @InstallIn(SingletonComponent)
│   ├── expenses/
│   │   ├── ExpenseDto.kt              # Kotlin data class mirroring backend ExpenseResponse
│   │   ├── ExpenseCategory.kt         # Kotlin enum with displayName (mirrors backend enum)
│   │   ├── ExpensesRepository.kt      # @Singleton; wraps WalletApiService
│   │   ├── ExpensesViewModel.kt       # @HiltViewModel; StateFlow<ExpensesUiState>
│   │   └── ui/
│   │       ├── ExpensesScreen.kt      # Top-level Composable
│   │       ├── ExpenseListItem.kt     # Reusable item Composable
│   │       └── DateRangePickerModal.kt # DatePickerDialog wrapper
│   └── di/
│       └── NetworkModule.kt           # (moved from net/ — now a Hilt module)
└── java/com/dawidcisowski/walletassistant/
    └── net/
        ├── HmacUtil.java              # Unchanged from Phase 1
        ├── HmacSigningInterceptor.java # Unchanged from Phase 1
        └── WalletApiService.java       # Updated: suspend fun + ExpenseDto return type
```

### Pattern 1: Hilt Module for Networking

**What:** Convert the existing `NetworkModule` object to a proper Hilt `@Module` so `WalletApiService` is a `@Singleton` injected anywhere via constructor injection.

**When to use:** All phases — this is the foundation for all future ViewModels.

```kotlin
// Source: developer.android.com/training/dependency-injection/hilt-android
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ ->
            LocalDate.parse(json.asString)
        })
        .create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HmacSigningInterceptor())
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides
    @Singleton
    fun provideWalletApiService(okHttpClient: OkHttpClient, gson: Gson): WalletApiService {
        val rawUrl = BuildConfig.BACKEND_URL.trimEnd('/')
        return Retrofit.Builder()
            .baseUrl("$rawUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WalletApiService::class.java)
        }
}
```

### Pattern 2: ViewModel with StateFlow + FilterState

**What:** Two separate StateFlows — one for the loaded data (network state) and one for the filter state (UI state). Filtered list is derived via `combine()`.

**When to use:** Any screen that has both async data loading and synchronous client-side filtering.

```kotlin
// Source: developer.android.com/topic/libraries/architecture/viewmodel [ASSUMED pattern]
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repository: ExpensesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExpensesUiState>(ExpensesUiState.Loading)
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState.default())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    init {
        loadExpenses()
    }

    fun onDateRangeSelected(from: LocalDate, to: LocalDate) {
        _filterState.update { it.copy(dateRange = from to to) }
        loadExpenses(from, to)
    }

    fun onCategoryToggled(category: ExpenseCategory) {
        _filterState.update { state ->
            val updated = if (category in state.selectedCategories)
                state.selectedCategories - category
            else
                state.selectedCategories + category
            state.copy(selectedCategories = updated)
        }
        // Client-side only — no network call needed for category filter
    }

    private fun loadExpenses(
        from: LocalDate = _filterState.value.dateRange.first,
        to: LocalDate = _filterState.value.dateRange.second
    ) {
        viewModelScope.launch {
            _uiState.value = ExpensesUiState.Loading
            try {
                val all = repository.getExpenses(from, to)
                _uiState.value = ExpensesUiState.Success(all)
            } catch (e: Exception) {
                _uiState.value = ExpensesUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

### Pattern 3: Composable collecting from ViewModel

**What:** `collectAsStateWithLifecycle()` instead of `collectAsState()` — stops collecting when app is in background.

```kotlin
// Source: developer.android.com/topic/libraries/architecture/lifecycle [CITED]
@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()

    // Stateless rendering based on uiState and filterState
}
```

### Pattern 4: Gson LocalDate Deserializer

**What:** Gson cannot deserialize `java.time.LocalDate` by default. Register a custom deserializer in `GsonBuilder`.

**When to use:** Whenever backend returns `occurredDate` as `"2026-06-18"` (ISO date string).

```kotlin
// Source: backend ExpenseResponse.java + Gson documentation [ASSUMED pattern, standard]
GsonBuilder()
    .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ ->
        LocalDate.parse(json.asString)  // ISO 8601: "2026-06-18"
    })
    .create()
```

**Important:** `occurredAt` (Instant) is serialized as a timestamp string by the backend Jackson serializer. Gson will deserialize it as a String. In `ExpenseDto`, model `occurredAt` as `String` and parse to `Instant` manually, or model it directly as `String` for display purposes (Phase 2 doesn't display `occurredAt` directly).

### Pattern 5: DateRangePicker in DatePickerDialog

**What:** Material3 DateRangePicker wrapped in `DatePickerDialog` with "Zastosuj"/"Anuluj" buttons. Dates come out as `Long?` (epoch millis) and must be converted to `LocalDate`. [CITED: developer.android.com/develop/ui/compose/components/datepickers]

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    onDateRangeConfirmed: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = state.selectedStartDateMillis
                    val endMillis = state.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        onDateRangeConfirmed(
                            Instant.ofEpochMilli(startMillis)
                                .atZone(ZoneId.of("Europe/Warsaw"))
                                .toLocalDate(),
                            Instant.ofEpochMilli(endMillis)
                                .atZone(ZoneId.of("Europe/Warsaw"))
                                .toLocalDate()
                        )
                        onDismiss()
                    }
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
            ) { Text("Zastosuj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Wybierz zakres dat") },
            showModeToggle = false,
            modifier = Modifier.fillMaxWidth().height(500.dp)
        )
    }
}
```

**Gotcha:** `DateRangePicker` is annotated `@ExperimentalMaterial3Api` — all call sites and the containing composable must carry `@OptIn(ExperimentalMaterial3Api::class)`.

### Anti-Patterns to Avoid

- **Passing `Context` to NetworkModule provides:** The current `NetworkModule.walletApi(context)` passes Context to read `ANDROID_ID`. Phase 2 moves to `BuildConfig.DEVICE_ID` (already correct per D-07 / Phase 1 context). The Hilt module must NOT receive Context — use `BuildConfig.DEVICE_ID` directly.
- **Filtering in Composable:** Category filter logic must live in the ViewModel (D-09). Composable should receive an already-filtered list from `uiState`.
- **Calculating total in Composable:** Total is always computed in ViewModel before emitting `Success` state. Never compute `list.sumOf { it.amount }` inside a Composable.
- **Using `collectAsState()` instead of `collectAsStateWithLifecycle()`:** The latter properly stops collection in background, saving CPU/battery.
- **`Call<>` return type on Retrofit interface:** Phase 2 must migrate `WalletApiService.getExpenses()` from `Call<List<Object>>` to `suspend fun getExpenses(...): List<ExpenseDto>`. Retrofit 2.6+ supports suspend natively — no CoroutineCallAdapterFactory needed. [CITED: Retrofit 2.6.0 release notes]
- **Not guarding `DateRangePicker` confirm with null check:** Both `selectedStartDateMillis` and `selectedEndDateMillis` can be null. The confirm button must be disabled/no-op until both are non-null.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Dependency injection | Manual factory/locator pattern | Hilt `@Module` + `@HiltViewModel` | Hilt handles scope, lifecycle, and ViewModel creation — hand-rolled DI misses lifecycle-aware cleanup |
| Date range selection UI | Custom date picker from scratch | Material3 `DateRangePicker` | Handles edge cases: locale, accessibility, keyboard navigation, month navigation |
| State lifecycle management | Manual coroutine scope in Composable | `collectAsStateWithLifecycle()` in ViewModel | Wrong scope = memory leaks or missed updates on orientation change |
| Gson Java 8 date types | Custom string-parsing logic in ViewModel | `JsonDeserializer` registered in `GsonBuilder` | GsonBuilder adapter is the standard pattern; isolates parsing concern to the network layer |
| Client-side filter + sort | Complex state threading | `StateFlow.combine()` or derived computation in ViewModel | Ensures single source of truth; re-filter on every emission automatically |

**Key insight:** Jetpack ecosystem components handle a large surface of correctness problems (lifecycle, threading, memory leaks) that are easy to get wrong with custom solutions.

---

## Runtime State Inventory

> Not applicable — this is a greenfield Composable UI phase with no renames, no refactors, and no stored data migration. The network layer change (object → Hilt module) is a code restructure, not a rename.

---

## Common Pitfalls

### Pitfall 1: Kotlin/Compose Compiler Version Mismatch

**What goes wrong:** Build fails with cryptic error like `incompatible classes were found in module` or Compose preview breaks.
**Why it happens:** Compose compiler extension version must exactly match the Kotlin version. The project uses Kotlin 1.9.20 → Compose compiler must be 1.5.5.
**How to avoid:** Set `composeOptions { kotlinCompilerExtensionVersion = "1.5.5" }` in `build.gradle.kts`. Do NOT use the latest BOM (2026.06.00) — it requires Kotlin 2.0+. Use BOM 2024.09.00.
**Warning signs:** Gradle sync errors mentioning "incompatible Kotlin" or "compose compiler"; build output referencing API version mismatches.

### Pitfall 2: KAPT vs KSP for Hilt

**What goes wrong:** Hilt code generation silently fails or produces wrong code if KAPT and KSP are mixed.
**Why it happens:** The transition from KAPT to KSP requires using `ksp(...)` declarations for ALL Hilt dependencies, and the compiler plugin must be `com.google.devtools.ksp` not `kotlin-kapt`.
**How to avoid:** Use `ksp("com.google.dagger:hilt-compiler:2.57.1")` — NOT `hilt-android-compiler`. Do NOT apply the `kotlin-kapt` plugin alongside KSP.
**Warning signs:** `@HiltViewModel` annotated class not generating expected Hilt_MyViewModel component; `Unresolved reference: hiltViewModel` at runtime.

### Pitfall 3: Java 11 → 17 Upgrade Required

**What goes wrong:** Build fails if `compileOptions` stay at `JavaVersion.VERSION_11` while Hilt 2.57.1 requires Java 17.
**Why it happens:** Hilt 2.57.1 targets Java 17 byte code.
**How to avoid:** Upgrade both `compileOptions { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_17 }` and `kotlinOptions { jvmTarget = "17" }`. The local machine already runs Java 21, so this is purely a Gradle build config change.
**Warning signs:** `unsupported class file major version 61` at build time.

### Pitfall 4: `WalletApiService.java` Return Type Migration

**What goes wrong:** Retrofit suspend functions cannot return `Call<T>` — the `execute()` pattern breaks with coroutines.
**Why it happens:** The Phase 1 walking skeleton used `Call<List<Object>>.execute()` synchronously on a background dispatcher. Phase 2 uses `suspend fun getExpenses(): List<ExpenseDto>` which Retrofit 2.6+ handles natively.
**How to avoid:** Change `WalletApiService.java` return type to `List<ExpenseDto>` and add `suspend` keyword (in Kotlin) OR keep it Java but use `@GET` with `suspend` in a Kotlin wrapper. Cleanest: convert `WalletApiService` to Kotlin and use `suspend fun`.
**Warning signs:** `Call` return type with `suspend` produces a compile error; Retrofit throws `NetworkOnMainThreadException` if `.execute()` is called from a coroutine context.

### Pitfall 5: Gson Cannot Deserialize `LocalDate` or `Instant`

**What goes wrong:** Backend returns `occurredDate: "2026-06-18"` and `occurredAt: "2026-06-18T10:30:00Z"`. Gson throws `JsonSyntaxException: Unable to invoke no-args constructor for class java.time.LocalDate`.
**Why it happens:** Gson has no built-in support for `java.time.*` types.
**How to avoid:** Register custom `JsonDeserializer<LocalDate>` in `GsonBuilder`. For `occurredAt` (`Instant`): either register a deserializer, or model it as `String` in `ExpenseDto` (sufficient for Phase 2 — only date is displayed).
**Warning signs:** Crash on first successful HTTP response with `JsonSyntaxException`.

### Pitfall 6: `DateRangePicker` Requires `@OptIn(ExperimentalMaterial3Api::class)`

**What goes wrong:** Build error: `This declaration needs opt-in. Its usage should be marked with '@androidx.compose.material3.ExperimentalMaterial3Api'`.
**Why it happens:** `DateRangePicker` is still marked experimental in Material3 1.3.x.
**How to avoid:** Annotate the composable file or function with `@OptIn(ExperimentalMaterial3Api::class)`.
**Warning signs:** Compile-time warning/error whenever `DateRangePicker`, `rememberDateRangePickerState`, or `DatePickerDialog` is referenced.

### Pitfall 7: Epoch Millis in UTC vs. Warsaw Timezone

**What goes wrong:** DateRangePicker returns epoch millis in UTC midnight. Converting to `LocalDate` without timezone awareness shifts dates by ±1 day.
**Why it happens:** `Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)` on a Warsaw-zone selection can produce the previous day.
**How to avoid:** Always convert via `ZoneId.of("Europe/Warsaw")` — consistent with backend timezone in `AppProperties`. The `LocalDate` boundary logic in ViewModel init must also use Warsaw time: `ZonedDateTime.now(ZoneId.of("Europe/Warsaw")).toLocalDate()`.
**Warning signs:** Date range picker selects June 18 but backend query uses June 17 as the `from` parameter.

### Pitfall 8: `NetworkModule` Object vs. Hilt Module Naming Conflict

**What goes wrong:** There are now two files named `NetworkModule` — the existing `net/NetworkModule.kt` (Kotlin object) and the new Hilt `@Module`. Gradle compiles both and Hilt tries to use both.
**How to avoid:** Delete or replace `net/NetworkModule.kt` with the Hilt `@Module`. Move the file to `di/NetworkModule.kt` to signal its role. Remove the old object-based factory.
**Warning signs:** Duplicate class errors or `@Provides` methods in the same component.

---

## Code Examples

Verified patterns from official sources:

### ExpenseDto — Kotlin data class mirroring backend

```kotlin
// Mirrors: src/main/java/org/dawid/cisowski/.../expenses/api/ExpenseResponse.java
// Source: backend codebase (VERIFIED by direct file read)
data class ExpenseDto(
    val expenseId: String,
    val eventId: String,
    val amount: Double,       // BigDecimal not natively supported in Gson; Double sufficient for display
    val currency: String,
    val category: String,     // Raw string — map to ExpenseCategory enum in ViewModel
    val description: String,
    val merchant: String?,    // Nullable — omit from display when null/blank (D-11)
    val accountType: String,
    val occurredAt: String,   // Instant as ISO string — not displayed directly in Phase 2
    val occurredDate: String  // LocalDate as "2026-06-18" — parse to LocalDate in ViewModel
)
```

### FilterState sealed class

```kotlin
// [ASSUMED pattern — standard for Compose MVVM]
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
```

### ExpensesUiState sealed class

```kotlin
// Source: CONTEXT.md D-02 [VERIFIED from CONTEXT.md]
sealed class ExpensesUiState {
    object Loading : ExpensesUiState()
    data class Success(
        val expenses: List<ExpenseDto>,   // already filtered + sorted
        val total: Double                 // sum of filtered amounts
    ) : ExpensesUiState()
    data class Error(val message: String) : ExpensesUiState()
}
```

### WalletApiService — migrated to Kotlin suspend

```kotlin
// Migration from Phase 1 Java interface to Kotlin suspend
// Source: CONTEXT.md code_context section [VERIFIED by file read]
interface WalletApiService {
    @GET("v1/expenses")
    suspend fun getExpenses(
        @Query("from") from: String,
        @Query("to") to: String
    ): List<ExpenseDto>
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| KAPT for Hilt annotation processing | KSP (`com.google.devtools.ksp`) | Hilt 2.48+ (2023) | 2x faster builds; `hilt-android-compiler` → `hilt-compiler` with `ksp(...)` |
| `composeOptions { kotlinCompilerExtensionVersion }` | Compose Compiler Gradle Plugin (Kotlin 2.0+) | Kotlin 2.0 (2024) | Project is on Kotlin 1.9.20 — must still use explicit `composeOptions` |
| `collectAsState()` | `collectAsStateWithLifecycle()` | lifecycle-runtime-compose 2.6.0 (2023) | Lifecycle-aware: stops collection in background |
| Manual `CoroutineCallAdapterFactory` | Retrofit native `suspend` support | Retrofit 2.6.0 (2019) | No adapter library needed; `suspend fun` directly in interface |
| `hilt-lifecycle-viewmodel` (deprecated) | `hilt-navigation-compose` | AndroidX Hilt 1.0 (2021) | Old artifact removed; `hiltViewModel()` is now in `hilt-navigation-compose` |

**Deprecated/outdated:**
- `androidx.hilt:hilt-lifecycle-viewmodel`: Removed — use `androidx.hilt:hilt-navigation-compose`
- `com.google.dagger:hilt-android-compiler` with KSP: Fails — use `com.google.dagger:hilt-compiler`
- `kotlin-kapt` plugin alongside KSP: Do not mix — pick one processor
- `com.jakewharton.retrofit2:retrofit2-kotlin-coroutines-adapter`: Obsolete since Retrofit 2.6.0

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | BOM 2024.09.00 is compatible with Kotlin 1.9.20 and compileExtension 1.5.5 | Standard Stack | Build fails at sync time — easy to detect; fix by adjusting BOM version |
| A2 | `hilt-navigation-compose:1.3.0` is compatible with Hilt 2.57.1 | Standard Stack | Hilt internal API mismatch; would surface as compile-time error |
| A3 | `ExpenseDto.amount` modeled as `Double` is sufficient for summing and displaying totals in Phase 2 | Code Examples | Floating-point rounding in totals — acceptable for Phase 2; Phase 4 handles polish |
| A4 | `WalletApiService` can be migrated from Java to Kotlin without regressions | Architecture Patterns | HmacSigningInterceptor is Java — interop should be fine; test HMAC signing after migration |
| A5 | `DateRangePicker` is stable enough in Material3 1.3.x for production use despite `@ExperimentalMaterial3Api` | Common Pitfalls | API could change in future Material3 upgrades; acceptable risk for personal-use app |
| A6 | KSP version 1.9.20-1.0.14 is the correct version for Kotlin 1.9.20 | Standard Stack | If wrong, Gradle sync fails with version mismatch error — easy to resolve |

---

## Open Questions

1. **Should `WalletApiService` be migrated from Java to Kotlin in Phase 2?**
   - What we know: The Java interface uses `Call<>` which must change to `suspend fun` for coroutines. The `net/` package convention from Phase 1 places this file in Java (D-02 of Phase 1 CONTEXT.md).
   - What's unclear: D-02 says "`net/` package in Java" but `suspend` functions cannot be declared in Java. Options: (a) keep Java + add a Kotlin wrapper; (b) migrate `WalletApiService.java` to Kotlin; (c) keep Java `Call<>` and wrap it with `suspendCancellableCoroutine` in the Repository.
   - Recommendation: Migrate `WalletApiService` to Kotlin (`WalletApiService.kt`) to use `suspend fun` directly. Update D-02 caveat: Java-in-net is for HMAC utilities which have no Kotlin-specific requirement; the Retrofit interface is better in Kotlin. `HmacUtil.java` and `HmacSigningInterceptor.java` stay Java (no Kotlin dependency needed).

2. **Where exactly does client-side filtering happen — ViewModel or Repository?**
   - What we know: D-09 says filtering is client-side; ViewModel knows the selected categories.
   - What's unclear: Should `ExpensesRepository` return the raw full list, and the ViewModel filters? Or should the Repository accept categories?
   - Recommendation: Repository returns the raw list (it only knows about network). ViewModel holds filter state and derives the filtered list for `ExpensesUiState.Success`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Hilt 2.57.1 (requires Java 17+) | Yes | OpenJDK 21.0.1 | — |
| Android SDK | Build | Yes | SDK 35 (confirmed from build.gradle.kts compileSdk=35) | — |
| Gradle 8.13 | Build | Yes | 8.13 (from gradle-wrapper.properties) | — |
| AGP 8.13.0 | Compose buildFeatures | Yes | 8.13.0 (from root build.gradle.kts) | — |
| Kotlin 1.9.20 | Compose compiler 1.5.5 | Yes | 1.9.20 (from root build.gradle.kts) | — |
| WalletAssistant backend | API calls | Assumed running locally | N/A | N/A — personal dev setup |

**Missing dependencies with no fallback:** none
**Missing dependencies with fallback:** none

---

## Validation Architecture

> `workflow.nyquist_validation` is `false` in `.planning/config.json`. Validation Architecture section skipped.

---

## Security Domain

> `security_enforcement` is enabled (true). ASVS Level 1 applies.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No (read-only screen; auth handled by Phase 1 HMAC interceptor) | HmacSigningInterceptor (already built) |
| V3 Session Management | No (no session tokens; HMAC per-request) | N/A |
| V4 Access Control | No (single-user personal app, no roles) | N/A |
| V5 Input Validation | Yes (date inputs from DateRangePicker) | Material3 `DateRangePicker` validates internally; ViewModel guards null before API call |
| V6 Cryptography | No new crypto (HMAC signing already complete in Phase 1) | HmacUtil.java unchanged |

### Known Threat Patterns for Android / Compose stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Log leakage of expenses data | Information Disclosure | Never log `ExpenseDto` fields; use `BuildConfig.DEBUG` guard on `HttpLoggingInterceptor` |
| Cleartext traffic on non-local networks | Spoofing | `usesCleartextTraffic="true"` is already in manifest for local dev; acceptable for personal-use app; document not to use on public Wi-Fi |
| HMAC secret in crash logs | Information Disclosure | `BuildConfig.HMAC_SECRET` must never appear in `Log.*` calls; Hilt module must not log the secret |

---

## Sources

### Primary (MEDIUM confidence — web-verified against official docs)
- [developer.android.com/training/dependency-injection/hilt-android](https://developer.android.com/training/dependency-injection/hilt-android) — Hilt setup, @HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel, latest version 2.57.1
- [developer.android.com/training/dependency-injection/hilt-jetpack](https://developer.android.com/training/dependency-injection/hilt-jetpack) — hiltViewModel() and hilt-navigation-compose 1.3.0
- [developer.android.com/develop/ui/compose/components/datepickers](https://developer.android.com/develop/ui/compose/components/datepickers) — DateRangePicker API, state, Dialog pattern, null guard
- [developer.android.com/jetpack/androidx/releases/compose-kotlin](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) — Compose compiler 1.5.5 compatible with Kotlin 1.9.20
- [developer.android.com/develop/ui/compose/bom/bom-mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — BOM 2024.09.00 version mapping

### Secondary (LOW confidence — web search summaries)
- Retrofit 2.6.0+ built-in suspend support — no CoroutineCallAdapterFactory needed
- KSP 1.9.20-1.0.14 for Kotlin 1.9.20; KSP is Google-recommended replacement for KAPT
- `collectAsStateWithLifecycle` requires `lifecycle-runtime-compose:2.8.x`

### Codebase (HIGH confidence — direct file reads)
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/build.gradle.kts` — confirmed Kotlin 1.9.20, AGP 8.13.0, Java 11 (must upgrade to 17), no Compose yet
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/.../net/NetworkModule.kt` — confirmed object singleton pattern with Context, ANDROID_ID usage
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/.../net/WalletApiService.java` — confirmed `Call<List<Object>>` return type
- `src/main/java/.../expenses/api/ExpenseResponse.java` — confirmed field names and types
- `src/main/java/.../expenses/ExpenseCategory.java` — confirmed 13 categories and Polish displayName() values
- `.planning/phases/02-expenses-screen/02-CONTEXT.md` — all decisions D-01 through D-14
- `.planning/phases/02-expenses-screen/02-UI-SPEC.md` — complete component and copywriting spec

---

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM — library names verified against official Android docs; versions confirmed via web fetch of official release pages. BOM version for Kotlin 1.9.20 is confirmed as 2024.09.00. KSP version 1.9.20-1.0.14 is [ASSUMED] — verify exact compatible KSP version at sync time.
- Architecture: HIGH — derived directly from existing codebase + locked decisions in CONTEXT.md
- Pitfalls: MEDIUM — most pitfalls derived from known Android ecosystem constraints; confirmed by official documentation

**Research date:** 2026-06-18
**Valid until:** 2026-09-18 (stable — Android ecosystem versions move slowly)
