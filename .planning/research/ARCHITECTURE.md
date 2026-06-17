# Architecture Patterns

**Project:** WalletAssistant Android
**Researched:** 2026-06-17
**Scope:** Kotlin + Jetpack Compose read-only viewer connecting to Spring Boot on Cloud Run

---

## Recommended Architecture

**Clean Architecture + MVVM, three layers, unidirectional data flow.**

```
┌─────────────────────────────────────────────────────────────┐
│  PRESENTATION LAYER                                          │
│                                                             │
│  Scaffold                                                   │
│  ├── NavigationBar (Expenses | Accounts | Investments)      │
│  └── NavHost                                                │
│       ├── ExpensesScreen ← ExpensesViewModel                │
│       ├── AccountsScreen ← AccountsViewModel                │
│       └── InvestmentsScreen ← InvestmentsViewModel          │
│                                                             │
│  Each ViewModel exposes: StateFlow<UiState>                 │
│  Composables collect with collectAsStateWithLifecycle()     │
└────────────────────────┬────────────────────────────────────┘
                         │ calls (suspend funs / Flow)
┌────────────────────────▼────────────────────────────────────┐
│  DOMAIN LAYER (optional for read-only app — keep thin)      │
│                                                             │
│  Domain models: Expense, AccountBalance, Investment         │
│  No business logic needed beyond mapping API → domain.      │
│  Skip UseCases unless filtering logic grows complex.        │
└────────────────────────┬────────────────────────────────────┘
                         │ calls (suspend funs)
┌────────────────────────▼────────────────────────────────────┐
│  DATA LAYER                                                 │
│                                                             │
│  ExpensesRepository                                         │
│  AccountsRepository                                         │
│  InvestmentsRepository                                      │
│       │                                                     │
│       └── WalletAssistantApiService  (Retrofit interface)   │
│                 │                                           │
│                 └── OkHttpClient                            │
│                       └── HmacSigningInterceptor            │
│                             └── HmacUtil.kt (copied)        │
└─────────────────────────────────────────────────────────────┘
                         │ HTTPS
┌────────────────────────▼────────────────────────────────────┐
│  BACKEND (Cloud Run)                                        │
│  Spring Boot 4.1 — /v1/expenses, /v1/accounts/*,           │
│  /v1/investments/*                                         │
└─────────────────────────────────────────────────────────────┘
```

---

## Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `HmacSigningInterceptor` | Computes HMAC-SHA256, injects `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature` on every outgoing request | OkHttpClient (registered as application interceptor) |
| `WalletAssistantApiService` | Retrofit interface — `@GET` declarations for all three query endpoints | OkHttpClient via Retrofit |
| `*Repository` (3 classes) | Maps Retrofit DTOs to domain models, wraps network calls in `Result<T>`, exposes suspend funs | `WalletAssistantApiService` |
| `*ViewModel` (3 classes) | Holds `StateFlow<UiState>`, calls repository, drives loading/error/success transitions | `*Repository`, Hilt |
| `*Screen` composables (3 screens) | Observe ViewModel state, render lists/cards, pass events up | Respective `*ViewModel` via `hiltViewModel()` |
| `MainNavGraph` | `NavHost` + `Scaffold` + `NavigationBar` — owns top-level navigation | `NavController`, all Screen composables |
| `NetworkModule` (Hilt) | Provides `OkHttpClient`, `Retrofit`, all `ApiService` and `Repository` singletons | Hilt dependency graph |

---

## HMAC Authentication Integration Point

**Use an OkHttp application-level Interceptor — this is the only correct integration point.**

Rationale: an interceptor runs once per logical request, has access to the full request (method, path, body) needed to build the canonical string, and centralises signing so no Retrofit call site needs to know about auth.

```kotlin
class HmacSigningInterceptor(
    private val deviceId: String,
    private val secret: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val timestamp = Instant.now().epochSecond.toString()
        val nonce = UUID.randomUUID().toString()
        val body = original.body?.let { buf ->
            val buffer = okio.Buffer()
            buf.writeTo(buffer)
            buffer.readUtf8()
        } ?: ""

        // Canonical string matches backend: METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY
        val canonical = buildString {
            append(original.method); append('\n')
            append(original.url.encodedPath); append('\n')
            append(timestamp); append('\n')
            append(nonce); append('\n')
            append(deviceId); append('\n')
            append(body)
        }

        val signature = HmacUtil.sign(canonical, secret)  // copied from HealthAssistant

        val signed = original.newBuilder()
            .header("X-Device-Id", deviceId)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .build()

        return chain.proceed(signed)
    }
}
```

Register in `NetworkModule`:
```kotlin
OkHttpClient.Builder()
    .addInterceptor(HmacSigningInterceptor(BuildConfig.DEVICE_ID, BuildConfig.HMAC_SECRET))
    .build()
```

**Do NOT** use an `Authenticator` (that is for reactive 401-retry flows, not proactive signing). **Do NOT** add headers in Repository or ViewModel — that leaks auth concern into higher layers.

---

## Data Flow (API to UI)

```
Backend REST endpoint
        │  JSON response
        ▼
Retrofit DTO (data class, @Json annotations)
        │  deserialized by Moshi / Gson
        ▼
Repository.getSomething(): Result<List<DomainModel>>
        │  maps DTO → domain model, wraps errors in Result.Failure
        ▼
ViewModel.load()   [viewModelScope.launch]
        │  emits Loading → Success/Error
        ▼
StateFlow<UiState>  (sealed class: Loading | Success | Error)
        │  collectAsStateWithLifecycle() in Composable
        ▼
Screen composable renders list / empty state / error message
```

**UiState pattern:**

```kotlin
sealed class ExpensesUiState {
    data object Loading : ExpensesUiState()
    data class Success(val expenses: List<Expense>) : ExpensesUiState()
    data class Error(val message: String) : ExpensesUiState()
}
```

ViewModel always starts in `Loading`, making the initial render consistent.

---

## Navigation Architecture

Use **Navigation Compose 2.x** (current stable 2.9.x). Do NOT use Nav3 — announced May 2025 but still pre-stable.

```
MainActivity
  └── MainNavGraph
        └── Scaffold(
              bottomBar = { NavigationBar { ... } },
              content  = { NavHost(navController, startDest = "expenses") {
                             composable("expenses") { ExpensesScreen() }
                             composable("accounts") { AccountsScreen() }
                             composable("investments") { InvestmentsScreen() }
                           } }
            )
```

Bottom nav item click:
```kotlin
navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

Each tab restores its scroll position and filter state via `saveState`/`restoreState` — critical for a smooth UX when switching tabs.

---

## Cloud Run Connection

Cloud Run provides a managed HTTPS URL (`https://<hash>-<region>.a.run.app`) with Google-managed TLS. No SSL pinning or custom trust manager is required — the standard Android trust store covers Google's certificates.

Configuration pattern via `local.properties` + `BuildConfig`:

```
# local.properties (git-ignored)
backendUrl=https://walletassistant-abc123-ew.a.run.app
deviceId=my-android-device
hmacSecret=super-secret-value
```

```kotlin
// build.gradle.kts
val localProps = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}
android {
    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_URL", "\"${localProps["backendUrl"]}\"")
            buildConfigField("String", "DEVICE_ID",   "\"${localProps["deviceId"]}\"")
            buildConfigField("String", "HMAC_SECRET", "\"${localProps["hmacSecret"]}\"")
        }
        release { /* same pattern */ }
    }
}
```

`local.properties` is in `.gitignore` by default in every Android project — secret never enters version control.

---

## Dependency Injection

Use **Hilt 2.56 + KSP** (not kapt — kapt is deprecated). One Hilt module suffices for this app:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideHmacInterceptor(): HmacSigningInterceptor = ...

    @Provides @Singleton
    fun provideOkHttp(interceptor: HmacSigningInterceptor): OkHttpClient = ...

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): WalletAssistantApiService = ...

    @Provides @Singleton
    fun provideExpensesRepository(api: WalletAssistantApiService): ExpensesRepository = ...

    // repeat for Accounts, Investments
}
```

ViewModels use `@HiltViewModel` + `hiltViewModel()` in composables — no manual ViewModel factory.

---

## Patterns to Follow

### Pattern 1: Repository as the only async boundary

Every network call lives in a Repository method marked `suspend`. ViewModels call repositories inside `viewModelScope.launch`. Composables never call suspend functions directly.

### Pattern 2: Sealed UiState over raw data

Never expose `List<T>?` from a ViewModel. Always wrap in a sealed UiState so the UI handles loading and error declaratively. This eliminates null checks and conditional rendering scattered across composables.

### Pattern 3: Stateless leaf composables

`ExpensesScreen` is stateful (receives `viewModel: ExpensesViewModel`). `ExpenseCard`, `AccountSummaryRow`, `InvestmentTile` are stateless (receive plain data parameters). This makes leaf composables trivially testable without a ViewModel.

### Pattern 4: Read-only pull-to-refresh

Since there is no write path on Android, the only user action that triggers network activity is pull-to-refresh. Add `SwipeRefresh` (or Compose Material3's `PullToRefreshBox`) at screen level. Filter/date pickers are local UI state changes that filter an already-fetched list in-memory — no extra API call needed for category filtering.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Adding HMAC headers in Repository

**What happens:** Repository manually appends headers on each call.
**Why bad:** Every new endpoint must be updated; logic is duplicated across 3 repositories; unit tests must mock header injection.
**Instead:** Interceptor signs every request once at the OkHttp layer.

### Anti-Pattern 2: LiveData instead of StateFlow

**What happens:** ViewModel uses `MutableLiveData<List<Expense>>`.
**Why bad:** LiveData is not coroutine-native; no integration with Flow operators; requires lifecycle observation boilerplate in Compose via `observeAsState`.
**Instead:** `MutableStateFlow<UiState>` exposed as `StateFlow`, collected with `collectAsStateWithLifecycle`.

### Anti-Pattern 3: Network calls from Composables

**What happens:** `LaunchedEffect(Unit) { val data = apiService.getExpenses(); ... }` in a composable.
**Why bad:** No lifecycle management; recomposition retriggers; no error handling layer; untestable.
**Instead:** All network calls in ViewModel/Repository; composable only observes StateFlow.

### Anti-Pattern 4: Hardcoding BACKEND_URL

**What happens:** `"https://walletassistant-abc123-ew.a.run.app"` literal in source code.
**Why bad:** URL changes when redeploying; secret URL in git history.
**Instead:** `BuildConfig.BACKEND_URL` from `local.properties`.

---

## Build Order (Phase Dependencies)

The components have a strict dependency chain that dictates implementation order:

```
1. HmacUtil.kt + HmacSigningInterceptor
        ↓  (requires HmacUtil)
2. WalletAssistantApiService + Retrofit/OkHttp setup (NetworkModule)
        ↓  (requires ApiService)
3. DTOs + Repository implementations
        ↓  (requires Repository)
4. ViewModels (ExpensesViewModel, AccountsViewModel, InvestmentsViewModel)
        ↓  (requires ViewModels)
5. Individual Screen composables (ExpensesScreen, AccountsScreen, InvestmentsScreen)
        ↓  (requires all screens)
6. MainNavGraph + Scaffold + Bottom NavigationBar
        ↓  (requires NavGraph)
7. MainActivity (wires Hilt + Nav entry point)
```

Each step is a natural phase boundary. Steps 1–3 are pure data layer and can be built and tested without any Compose dependency. Steps 4–5 can be built per-domain-area in parallel. Steps 6–7 are the integration step.

---

## Scalability Considerations

This is a personal read-only app — scalability concerns are minimal. The relevant operational concern is Cloud Run cold start latency (JVM startup ~2–4s). Mitigation: configure Cloud Run with `--min-instances=1` to keep one instance warm, eliminating cold starts for personal use at negligible cost.

| Concern | Approach |
|---------|----------|
| Cold start latency | `--min-instances=1` on Cloud Run service |
| Token/secret rotation | Update `local.properties`, rebuild APK — acceptable for personal use |
| API changes | Retrofit DTOs decouple Android from backend field names; only DTO + domain mapper changes |
| Screen count growth | NavGraph and Hilt module are structured to add new tabs without touching existing screens |

---

## Sources

- [Compose UI Architecture — Android Developers](https://developer.android.com/develop/ui/compose/architecture) (official)
- [Dependency injection with Hilt — Android Developers](https://developer.android.com/training/dependency-injection/hilt-android) (official)
- [Navigation with Compose — Android Developers](https://developer.android.com/develop/ui/compose/navigation) (official)
- [Announcing Jetpack Navigation 3 — Android Developers Blog](https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html) (May 2025 — Nav3 pre-stable, use Nav2)
- [Invoke with an HTTPS Request — Cloud Run Documentation](https://docs.cloud.google.com/run/docs/triggering/https-request) (official)
- [Modeling Retrofit Responses With Sealed Classes and Coroutines — GetStream](https://getstream.io/blog/modeling-retrofit-responses/) (web, cross-checked pattern)
- [Automating Auth Token Injection in Retrofit with OkHttp Interceptors — DEV Community](https://dev.to/hyuwah/automating-auth-token-injection-in-retrofit-with-okhttp-interceptors-1kjj) (web)
- [Gradle BuildConfig and Secrets Management — ProAndroidDev](https://proandroiddev.com/gradle-properties-buildconfig-and-secrets-management-the-right-way-8b1b161aaefd) (web)

**Confidence: MEDIUM** — official Android documentation (HIGH) cross-checked with multiple community sources (LOW); merged verdict MEDIUM. Core patterns (MVVM+StateFlow, OkHttp Interceptor, Hilt) are well-established with official documentation backing.
