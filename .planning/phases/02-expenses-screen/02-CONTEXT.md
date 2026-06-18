# Phase 2: Expenses Screen - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the full Expenses screen in the Android app: reverse-chronological list of expenses for the current month (default), date range selection (quick chips + modal picker), multi-select category filter chips, and a total spend summary card. Establishes the MVVM + Hilt + StateFlow pattern that Phase 3 (Accounts + Investments) will reuse.

No account or investment screens. No data-entry flow. No charts. No currency formatting polish (Phase 4). This is the read-only expenses view with filtering.

</domain>

<decisions>
## Implementation Decisions

### MVVM Architecture

- **D-01:** Dependency injection via **Hilt** — `@HiltViewModel` on `ExpensesViewModel`, `hiltViewModel()` in Composable. App class annotated `@HiltAndroidApp`. This pattern is the template for Phase 3 and 4 screens.
- **D-02:** UI state modeled as **StateFlow + sealed class**: `sealed class ExpensesUiState { Loading, Success(expenses: List<Expense>, total: BigDecimal), Error(message: String) }`. Collected in Composable via `collectAsState()`.
- **D-03:** **Repository layer** — `ExpensesRepository` wraps `WalletApiService` calls. ViewModel injects repository, not the API interface directly. Keeps ViewModel free of Retrofit concerns.

### Date Picker UX

- **D-04:** Quick-access filter chips at the top of screen: **"Ten miesiąc"** (default on first open) / **"Poprzedni"** / **"Niestandardowy"**. Tapping "Niestandardowy" opens a Material3 `DateRangePicker` in a modal bottom sheet or dialog.
- **D-05:** Active date range displayed as compact text: `"1 cze — 18 cze 2026"` in a chip/label above the expense list. Tapping it opens the picker for modification.
- **D-06:** Default range = current calendar month (first day of month → today). Determined at ViewModel init, not in Composable.

### Category Filter Chips

- **D-07:** Show **all 13 categories** including `SAVINGS_TRANSFER`. Backend can return expenses with this category; hiding it would cause confusion when "All" shows items with no visible chip.
- **D-08:** Layout: **horizontal `LazyRow` of `FilterChip`** components. Standard Android pattern (cf. Google News, YouTube filters). Single scrollable row — does not wrap.
- **D-09:** Selection logic: **no chip selected = show all expenses** (chips narrow the view). Multiple chips can be active simultaneously (per EXP-03). Filtering is **client-side** — the backend has no category filter endpoint; ViewModel filters the loaded list.
- **D-10:** Display names use Polish labels from `ExpenseCategory.displayName()` (e.g., "Jedzenie i napoje", "Transport").

### Expense List Item Layout

- **D-11:** Each expense item is a two-row card/row:
  - Row 1 (top): `description` (primary, left-aligned) | `amount` (bold, right-aligned)
  - Row 2 (bottom): `merchant` (secondary, muted — omit if null/empty) + `category displayName` (muted) — left | `occurredDate` (muted) — right
- **D-12:** Category display on list items: **Polish text label** from `ExpenseCategory.displayName()`. No color indicators or icons in Phase 2.
- **D-13:** Amount format: raw number + currency string for Phase 2 (e.g., "123.45 PLN"). Full Polish formatting ("1 234,56 zł") is Phase 4 scope (APP-04).

### Total Spend Card

- **D-14:** Total is **always calculated client-side** from the currently displayed expense list (after date range + category filters applied). No call to `/v1/expenses/summary/monthly`. Rationale: backend monthly summary doesn't support arbitrary date ranges or category filters — client-side is the only consistent approach. No pagination in v1, so the loaded list is complete.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planning Context
- `.planning/ROADMAP.md` — Phase 2 goal, success criteria (EXP-01–EXP-04), dependency on Phase 1
- `.planning/REQUIREMENTS.md` — EXP-01 through EXP-04 acceptance criteria; note EXP-03 category list has 12 categories but backend has 13 (SAVINGS_TRANSFER — show all 13 per D-07)
- `.planning/PROJECT.md` — project constraints (Android read-only, HMAC auth, personal use)
- `.planning/phases/01-android-foundation/01-CONTEXT.md` — Phase 1 decisions (D-01 through D-07): package name, Java/Kotlin split, build config, HMAC pattern

### Android Project (Phase 1 output — extend, don't replace)
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/net/NetworkModule.kt` — existing Retrofit/OkHttp setup. **Important:** currently takes `Context` to read `ANDROID_ID` as device ID (implementation note: walking skeleton used ANDROID_ID at runtime, verified HTTP 200 — D-07 from Phase 1 CONTEXT.md specified BuildConfig.DEVICE_ID but code uses ANDROID_ID; both are registered in backend). Phase 2 should move this to a Hilt-injectable singleton — no longer pass Context per call.
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/java/com/dawidcisowski/walletassistant/net/WalletApiService.java` — Retrofit interface. Has `getExpenses(from, to)` returning `List<Object>` — **must be changed to typed `List<Expense>` in Phase 2**.
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/build.gradle.kts` — needs new dependencies: Hilt, Jetpack Compose + Material3, Coroutines.
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt` — walking skeleton with verification call. Phase 2 replaces with Compose `setContent { }`.
- `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/WalletAssistantApp.kt` — empty Application class. Phase 2 adds `@HiltAndroidApp` annotation.

### Backend API Contract (read-only reference)
- `src/main/java/org/dawid/cisowski/walletassistant/expenses/api/ExpenseResponse.java` — response model to mirror as Android data class: `expenseId`, `eventId`, `amount` (BigDecimal), `currency`, `category`, `description`, `merchant`, `accountType`, `occurredAt` (Instant), `occurredDate` (LocalDate)
- `src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpenseCategory.java` — enum with Polish `displayName()` — defines the 13 categories and their display names
- `src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpensesController.java` — confirms endpoint: `GET /v1/expenses?from=&to=` (both required, ISO date format). No category query param — filtering is client-side.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NetworkModule.kt`: OkHttp + Retrofit + HmacSigningInterceptor already wired. Phase 2 refactors to `@Singleton` Hilt module — `@Provides @Singleton fun provideWalletApiService(...)`. Remove Context parameter (move ANDROID_ID read to Hilt provision, or use BuildConfig.DEVICE_ID).
- `HmacSigningInterceptor.java`: Complete, tested, no changes needed. Just inject via Hilt.
- `HmacUtil.java`: Complete, no changes needed.
- `WalletApiService.java`: Has `getExpenses(from, to)` — change return type from `List<Object>` to `Call<List<ExpenseDto>>` (or `suspend fun` with Retrofit Kotlin coroutines adapter).

### Established Patterns from Phase 1
- Java for `net/` package (D-02): New Android-side DTOs (`ExpenseDto.kt`) should be Kotlin Records/data classes — they are NOT in `net/`, they are domain models
- Kotlin DSL for all build scripts (D-03)
- BuildConfig for secrets (D-05, D-06): No new secrets needed for Phase 2

### Integration Points
- `GET /v1/expenses?from=YYYY-MM-DD&to=YYYY-MM-DD` is the only API call Phase 2 makes
- The `occurredDate` field (LocalDate) from backend — Gson cannot deserialize LocalDate out of the box. Needs a custom `JsonDeserializer<LocalDate>` or switch to string and parse manually in the Android model.
- `amount` is BigDecimal — Gson serializes as number. Safe to deserialize as `Double` or `String` on Android for display purposes in Phase 2 (BigDecimal not available in Android without special handling).

</code_context>

<specifics>
## Specific Ideas

- Expense list item layout (D-11): description primary, merchant secondary. When merchant is null/empty, show only description (don't show empty line).
- Quick date chips (D-04): "Ten miesiąc" / "Poprzedni" / "Niestandardowy" — exactly these three. Tapping "Niestandardowy" opens Material3 `DateRangePicker`.
- Category chip filter (D-09): Show all 13 in `LazyRow`. Initial state: no chips selected (= show all). Selecting chips narrows the visible list. Client-side filter only.
- Total card (D-14): Sum calculated on-the-fly from the displayed expense list after all filters. Shows the period in the card subtitle (e.g., "Czerwiec 2026" or the selected range).

</specifics>

<deferred>
## Deferred Ideas

- Polish currency formatting ("1 234,56 zł") — explicitly Phase 4 scope (APP-04). Phase 2 uses raw numbers.
- Expense item color coding by category — nice-to-have, not required for Phase 2.
- Charts / analytics (CHART-01 through CHART-04) — v2 requirements, out of scope.
- Dark mode (UX-01) — v2 requirements, out of scope.
- Cloud Run deploy (INFRA-01 through INFRA-04) — deferred to v2 per STATE.md.

</deferred>

---

*Phase: 2-Expenses Screen*
*Context gathered: 2026-06-18*
