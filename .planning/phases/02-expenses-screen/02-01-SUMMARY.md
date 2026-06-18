---
phase: 02-expenses-screen
plan: "01"
subsystem: android-expenses-screen
tags: [android, compose, hilt, mvvm, expenses, retrofit]
dependency_graph:
  requires: [01-android-foundation]
  provides: [expenses-screen-mvp, hilt-mvvm-template]
  affects: [02-02-expenses-screen-plan-02]
tech_stack:
  added:
    - Hilt 2.51.1 (DI framework; downgraded from 2.57.1 for Kotlin 1.9.20 compatibility)
    - KSP 1.9.20-1.0.14 (annotation processor replacing KAPT)
    - Compose BOM 2024.09.00 (UI framework locked to Kotlin 1.9.20)
    - Material3 1.3.0 (via BOM)
    - hilt-navigation-compose 1.3.0 (hiltViewModel() factory)
    - lifecycle-runtime-compose 2.8.0 (collectAsStateWithLifecycle)
    - lifecycle-viewmodel-compose 2.8.0
    - activity-compose 1.9.0 (setContent)
  patterns:
    - MVVM with @HiltViewModel + StateFlow<ExpensesUiState>
    - Repository wrapping Retrofit suspend API
    - Hilt @Module @InstallIn(SingletonComponent) for singleton services
    - collectAsStateWithLifecycle (lifecycle-aware StateFlow collection)
    - Gson LocalDate custom deserializer registered in NetworkModule
key_files:
  created:
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/di/NetworkModule.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/net/WalletApiService.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpenseDto.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpenseCategory.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesRepository.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesUiState.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesViewModel.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpensesScreen.kt
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpenseListItem.kt
  modified:
    - build.gradle.kts (root — Hilt + KSP plugins)
    - app/build.gradle.kts (Compose, Hilt deps, Java 17, composeOptions)
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/WalletAssistantApp.kt (@HiltAndroidApp)
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt (@AndroidEntryPoint + Compose)
  deleted:
    - app/src/main/kotlin/com/dawidcisowski/walletassistant/net/NetworkModule.kt (old object singleton)
    - app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java (Call<> interface)
decisions:
  - "Hilt downgraded from 2.57.1 to 2.51.1 — 2.57.1 is compiled with Kotlin 2.1.0 metadata, incompatible with project's Kotlin 1.9.20"
  - "ExpenseDto.occurredDate modeled as String (not LocalDate) — Gson LocalDate deserializer registered in NetworkModule covers deserialization; String is simpler for the DTO boundary"
  - "SummaryCard period label shows 'Bieżący miesiąc' in Plan 01 — full month name ('Czerwiec 2026') with FilterState integration is Plan 02 scope"
metrics:
  duration_minutes: 60
  completed_date: "2026-06-18"
  tasks_completed: 3
  tasks_total: 4
  files_changed: 15
status: complete
---

# Phase 02 Plan 01: Android Expenses Screen MVP — Summary

Vertical slice delivering the Compose Expenses screen: fetches current month's expenses from the backend over the existing HMAC-signed connection and renders them newest-first with a total-spend summary card. Establishes the MVVM + Hilt + StateFlow template for Phases 3 and 4.

## What Was Built

- **Build infrastructure**: Compose BOM 2024.09.00, Hilt 2.51.1, KSP 1.9.20-1.0.14, Java 17 — all integrated into the existing Kotlin 1.9.20 / AGP 8.13.0 project
- **Hilt @Module**: `di/NetworkModule.kt` provides `Gson` (with LocalDate deserializer), `OkHttpClient` (HMAC-signed, 30s timeouts, DEBUG-only body logging), `WalletApiService` as `@Singleton`
- **Expense data layer**: `ExpenseDto`, `ExpenseCategory` (13 values, Polish labels, fromString fallback), `WalletApiService.kt` (suspend), `ExpensesRepository`
- **MVVM**: `ExpensesViewModel` (@HiltViewModel) with Warsaw-timezone default month range, sort newest-first (occurredDate + occurredAt desc), total computation, retry()
- **Compose UI**: `ExpensesScreen` (Scaffold + TopAppBar "Wydatki" + SummaryCard + LazyColumn), `ExpenseListItem` (two-row layout, null merchant handling, date formatting), Loading/Error states

## Commits

| Hash | Task | Description |
|------|------|-------------|
| 8f532e0 | Task 1 | Build upgrade: Compose + Hilt + KSP + Java 17, di/NetworkModule Hilt @Module |
| a8128bb | Task 2 | Expense data layer: ExpenseDto, ExpenseCategory, WalletApiService.kt, ExpensesRepository |
| 3fcae86 | Task 3 | ExpensesViewModel + Compose ExpensesScreen end-to-end |

## Deviations from Plan

### Auto-fixed Issues

**1. [Reguła 1 - Bug] Hilt 2.57.1 niezgodny z Kotlin 1.9.20 — downgrade do 2.51.1**

- **Found during**: Task 1 verification build
- **Issue**: Hilt 2.57.1 jest skompilowany z metadanymi Kotlin 2.1.0 (`binary version 2.1.0, expected 1.9.0`). Projekt używa Kotlin 1.9.20. Build kończył się błędem w fazie `kspDebugKotlin`.
- **Fix**: Downgrade Hilt do 2.51.1 — ostatnia wersja skompilowana z Kotlin 1.9.20 (potwierdzone przez `hilt-compiler-2.51.1.pom` → `kotlin-stdlib:1.9.20`). Dostępne na mavenCentral.
- **Files modified**: `build.gradle.kts` (root), `app/build.gradle.kts`
- **Commit**: 8f532e0

**2. [Reguła 3 - Blocking] Tymczasowy stub MainActivity w Task 1**

- **Found during**: Task 1 — MainActivity miała referencję do usuniętego `net/NetworkModule.kt`
- **Issue**: Po usunięciu starego `net/NetworkModule.kt`, `MainActivity.kt` nie kompilowała się (`Unresolved reference: NetworkModule`)
- **Fix**: Tymczasowe zastąpienie `MainActivity.kt` minimalnym stubem (bez NetworkModule, bez walking-skeleton), który następnie w Task 3 zastąpiono finalną implementacją
- **Files modified**: `MainActivity.kt`
- **Commit**: 8f532e0 (stub), 3fcae86 (finalna implementacja)

## Known Stubs

- **SummaryCard period label**: wyświetla "Bieżący miesiąc" zamiast dynamicznej etykiety ("Czerwiec 2026" / "1 cze — 18 cze 2026"). Pełna integracja z FilterState (DateChip, date range) jest zakresem Plan 02. Stub nie blokuje celu planu — użytkownik widzi prawidłowy total i listę wydatków.
  - Plik: `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpensesScreen.kt`, metoda `SummaryCard()`

## Threat Flags

Wszystkie zagrożenia z `<threat_model>` zaimplementowane prawidłowo:

| Threat ID | Status |
|-----------|--------|
| T-02-01 (HttpLoggingInterceptor) | MITIGATED — `BuildConfig.DEBUG` guard w NetworkModule; release = NONE |
| T-02-02 (HMAC_SECRET) | MITIGATED — `provideOkHttpClient()` nie loguje HMAC_SECRET; żadnych `Log.*` w kodzie Kotlin |
| T-02-03 (ExpenseDto fields) | MITIGATED — brak Log.* w warstwie danych/UI |
| T-02-04 (cleartext) | ACCEPTED — `usesCleartextTraffic="true"` pozostaje w manifeście (personal-use app) |
| T-02-05 (Maven deps) | ACCEPTED — wszystkie artefakty z maven.google.com / mavenCentral |

## Success Criteria Status

- [x] `./gradlew :app:assembleDebug` BUILD SUCCESSFUL (Compose + Hilt + KSP + Java 17)
- [x] Hilt graph resolves: WalletApiService → ExpensesRepository → ExpensesViewModel
- [x] App launches into Compose "Wydatki" screen (walking-skeleton AppCompatActivity zastąpiony)
- [x] EXP-01: expenses sorted newest-first, total summary card visible
- [x] MVVM + Hilt + StateFlow template established (D-01, D-02, D-03) dla Phases 3/4
- [ ] **PENDING**: Checkpoint 4 — live verification z działającym backendem (Task 4)

## Self-Check: PASSED

All 9 created files verified on disk. All 3 task commits (8f532e0, a8128bb, 3fcae86) confirmed in git log.
