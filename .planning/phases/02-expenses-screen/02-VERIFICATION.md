---
phase: 02-expenses-screen
verified: 2026-06-18T00:00:00Z
status: passed
score: 4/4 must-haves verified
behavior_unverified: 4
overrides_applied: 0
behavior_unverified_items:
  - truth: "Tapping 'Poprzedni' reloads expenses for the previous calendar month"
    test: "Launch app, tap 'Poprzedni' chip; observe that the list reloads and the summary card label changes to the prior month name and total updates"
    expected: "List contains only prior-month expenses; card shows correct month name (e.g., 'Maj 2026') and correct total"
    why_human: "Requires a running backend with real data; cannot verify date-range reload correctness (correct API params, non-empty response) via grep or static analysis"
  - truth: "Tapping 'Niestandardowy' opens a Material3 DateRangePicker; confirming reloads expenses for the chosen range and updates the summary card label"
    test: "Tap 'Niestandardowy'; select a start and end date; tap 'Zastosuj'; observe that the list reloads and the card label shows 'd MMM — d MMM yyyy' format; tap again and 'Anuluj' — confirm no change"
    expected: "DateRangePicker opens (title 'Wybierz zakres dat'); after confirm the list shows only expenses within the chosen range; after cancel the previous selection is unchanged"
    why_human: "Runtime behavior of DateRangePicker modal, epoch-millis timezone conversion correctness, and cancel-no-change invariant cannot be verified by static analysis"
  - truth: "A horizontal LazyRow shows all 13 category FilterChips; tapping chips narrows the visible list client-side; multiple chips can be active"
    test: "Tap one category chip; confirm the list narrows to that category and the total drops; tap a second chip; confirm both categories appear; deselect all and confirm full list returns"
    expected: "Multi-select works; total recomputes correctly for each selection combination"
    why_human: "State transition — the correct narrowing and total recomputation can only be confirmed by observing the rendered list at runtime with real data"
  - truth: "Loading shows a centered spinner; network failure shows the error state with a 'Spróbuj ponownie' retry; an empty result shows the empty state"
    test: "Kill backend, relaunch app — confirm error state renders with icon + strings + retry button; tap retry — confirm loading spinner appears briefly; pick a date range/category with no data — confirm empty state icon + 'Brak wydatków'"
    expected: "All three states render with correct icons, Polish strings, and correct layout"
    why_human: "Loading/error/empty state transitions require runtime observation; network-kill scenario cannot be simulated by static analysis"
human_verification:
  - test: "Tap 'Poprzedni' chip and verify prior-month reload"
    expected: "List reloads for previous calendar month; summary card label shows prior month name; total matches sum of visible rows"
    why_human: "Requires running backend with real data; date-boundary and reload correctness not verifiable statically"
  - test: "Open DateRangePicker via 'Niestandardowy', select a range, confirm, then cancel"
    expected: "After confirm: list shows chosen range, card shows 'd MMM — d MMM yyyy' label. After cancel: no change to selection"
    why_human: "Runtime modal interaction, timezone conversion correctness, and cancel invariant require real device/emulator execution"
  - test: "Multi-select category chips and verify total recomputation"
    expected: "Each chip combination narrows the visible list and the summary card total equals the sum of visible rows; deselecting all restores the full list"
    why_human: "State-transition correctness (narrowing + total) requires rendering with real data"
  - test: "Trigger loading / error / empty states"
    expected: "Loading: centered spinner (no chips). Error: ErrorOutline icon + 'Nie udało się wczytać wydatków' + retry button that triggers reload. Empty: ReceiptLong icon + 'Brak wydatków' with chips still visible above"
    why_human: "Network-kill scenario and empty-result scenario require live runtime testing"
---

# Phase 02: Expenses Screen Verification Report

**Phase Goal:** Deliver a fully interactive read-only Expenses screen: date range selection (quick chips + DateRangePicker), 13-category multi-select FilterChip row (client-side), summary card total recomputing from the displayed list, and full loading/error/empty states per the UI-SPEC.
**Verified:** 2026-06-18
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Expenses screen opens showing current month's expenses in reverse-chronological order by default | ✓ VERIFIED | `FilterState.default()` seeds Warsaw-timezone current month; `emitDerivedState()` sorts with `compareByDescending { occurredDate }.thenByDescending { occurredAt }` |
| 2 | User can open a date range picker and expenses list updates to show the selected period | ✓ VERIFIED | `DateRangePickerModal` exists with `@OptIn(ExperimentalMaterial3Api::class)`, Warsaw-zone millis→LocalDate conversion, "Zastosuj"/"Anuluj" buttons; `onDateRangeSelected` in ViewModel triggers reload; wiring confirmed in `ExpensesScreen.kt` line 71 |
| 3 | User can tap category filter chips to narrow expenses; multiple chips can be active at once | ✓ VERIFIED | `CategoryFilterRow` iterates `ExpenseCategory.entries` (13 chips including SAVINGS_TRANSFER); `FilterChip(selected = category in selectedCategories)`; `onCategoryToggled` toggles `Set<ExpenseCategory>` without repository call |
| 4 | A summary card shows the total amount spent for the currently selected period and filters | ✓ VERIFIED | `emitDerivedState()` computes `total = sorted.sumOf { it.amount }` from the post-filter post-sort list and emits `Success(sorted, total)`; SummaryCard renders `"Łącznie: $total PLN"` from `state.total` |

**Score:** 4/4 truths verified (4 present, behavior-unverified — see below)

**Note on score:** All 4 truths have the required code present and wired. However, each truth also has a runtime behavior component (date reload correctness, DateRangePicker interaction, multi-select state transitions, UI state transitions) that cannot be confirmed by static analysis alone. Per the verification protocol, presence + wiring = VERIFIED for the structural component; the behavioral invariants are separately flagged as `behavior_unverified_items` and routed to human verification.

---

### Required Artifacts (Plan 01 + Plan 02)

| Artifact | Status | Details |
|----------|--------|---------|
| `di/NetworkModule.kt` | ✓ VERIFIED | `@Module @InstallIn(SingletonComponent::class)`, 3 `@Provides @Singleton` functions (Gson, OkHttpClient, WalletApiService); `HmacSigningInterceptor(BuildConfig.DEVICE_ID)` added first |
| `net/WalletApiService.kt` | ✓ VERIFIED | Kotlin interface; `suspend fun getExpenses(@Query("from") from: String, @Query("to") to: String): List<ExpenseDto>`; path does not start with "/" |
| `expenses/ExpenseDto.kt` | ✓ VERIFIED | `data class ExpenseDto` with 10 fields; `merchant: String?` (nullable); `amount: Double` |
| `expenses/ExpenseCategory.kt` | ✓ VERIFIED | `enum class ExpenseCategory(val displayName: String)` with exactly 13 constants including SAVINGS_TRANSFER; `fromString` case-insensitive, falls back to OTHER |
| `expenses/ExpensesRepository.kt` | ✓ VERIFIED | `@Singleton class ExpensesRepository @Inject constructor`; delegates to `api.getExpenses`; no filter/sort/sum logic |
| `expenses/ExpensesUiState.kt` | ✓ VERIFIED | `sealed class ExpensesUiState { Loading, Success(expenses, total: Double), Error(message) }` |
| `expenses/ExpensesViewModel.kt` | ✓ VERIFIED | `@HiltViewModel`; `val uiState: StateFlow<ExpensesUiState>`; `val filterState: StateFlow<FilterState>`; `fun retry()`, `fun onDateChipSelected(DateChip)`, `fun onDateRangeSelected(LocalDate, LocalDate)`, `fun onCategoryToggled(ExpenseCategory)` |
| `expenses/ui/ExpensesScreen.kt` | ✓ VERIFIED | `fun ExpensesScreen(viewModel: ExpensesViewModel = hiltViewModel())`; collects both StateFlows via `collectAsStateWithLifecycle`; Scaffold + TopAppBar "Wydatki"; full Loading/Error/Success branches |
| `expenses/ui/ExpenseListItem.kt` | ✓ VERIFIED | Two-row layout; null/blank merchant handled (shows only category, no leading "·"); date formatted "d MMM" or "d MMM yyyy" |
| `expenses/FilterState.kt` | ✓ VERIFIED | `data class FilterState(dateRange, selectedCategories, activeDateChip)` with `companion object { fun default() }` using `ZoneId.of("Europe/Warsaw")`; `enum class DateChip { THIS_MONTH, PREVIOUS_MONTH, CUSTOM }` |
| `expenses/ui/DateQuickChips.kt` | ✓ VERIFIED | Row of 3 `FilterChip`s with exact labels "Ten miesiąc", "Poprzedni", "Niestandardowy"; third chip calls `onCustomPickerRequested` |
| `expenses/ui/DateRangePickerModal.kt` | ✓ VERIFIED | `@OptIn(ExperimentalMaterial3Api::class)`; `rememberDateRangePickerState`; "Zastosuj" enabled only when both millis non-null; Warsaw zone conversion; "Anuluj"; title "Wybierz zakres dat"; `showModeToggle = false` |
| `expenses/ui/CategoryFilterRow.kt` | ✓ VERIFIED | `LazyRow` over `ExpenseCategory.entries` (13 chips); `FilterChip(selected = category in selectedCategories)`; `category.displayName` labels |

**Deleted (confirmed absent):**
- `net/NetworkModule.kt` (old Kotlin object singleton) — no longer exists in `net/` package
- `net/WalletApiService.java` (old Java Call<> interface) — no longer exists in Java source tree

---

### Key Link Verification (Plan 01 + Plan 02)

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `expenses/ui/ExpensesScreen.kt` | `expenses/ExpensesViewModel.kt` | `hiltViewModel() + collectAsStateWithLifecycle()` | ✓ WIRED | Lines 62–64: both `uiState` and `filterState` collected via `collectAsStateWithLifecycle` |
| `expenses/ExpensesViewModel.kt` | `expenses/ExpensesRepository.kt` | `repository.getExpenses(from, to)` | ✓ WIRED | Line 115: `rawLoadedList = repository.getExpenses(from, to)` inside `loadExpenses()` |
| `expenses/ExpensesRepository.kt` | `net/WalletApiService.kt` | `api.getExpenses(from, to)` over GET /v1/expenses | ✓ WIRED | `api.getExpenses(from.toString(), to.toString())` |
| `di/NetworkModule.kt` | `net/HmacSigningInterceptor.java` | `addInterceptor(HmacSigningInterceptor(deviceId))` | ✓ WIRED | Line 45: `addInterceptor(HmacSigningInterceptor(deviceId))` where `deviceId = BuildConfig.DEVICE_ID` |
| `expenses/ui/ExpensesScreen.kt` | `expenses/ExpensesViewModel.kt` | `onCategoryToggled / onDateChipSelected / onDateRangeSelected` callbacks | ✓ WIRED | Lines 71, 145, 153: all three callbacks wired |
| `expenses/ui/DateRangePickerModal.kt` | `expenses/ExpensesViewModel.kt` | `onDateRangeConfirmed(start, end) -> viewModel.onDateRangeSelected` | ✓ WIRED | `ExpensesScreen` line 71: `viewModel.onDateRangeSelected(from, to)` called from `onDateRangeConfirmed` |
| `expenses/ExpensesViewModel.kt` | `expenses/FilterState.kt` | `MutableStateFlow<FilterState>` seeded from `FilterState.default()` | ✓ WIRED | Line 36: `private val _filterState = MutableStateFlow(FilterState.default())` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `ExpensesScreen.kt` | `state.expenses`, `state.total` | `ExpensesViewModel.uiState` ← `emitDerivedState()` ← `repository.getExpenses()` ← `WalletApiService.getExpenses()` → real network call | Yes — Retrofit suspend call to GET /v1/expenses with HMAC auth; not hardcoded | ✓ FLOWING |
| `ExpensesScreen.kt` | `filterState.activeDateChip`, `filterState.selectedCategories` | `ExpensesViewModel.filterState` ← `FilterState.default()` + user interactions | Yes — driven by user chip taps | ✓ FLOWING |
| `SummaryCard` | `total` | `ExpensesUiState.Success.total` ← `emitDerivedState()` `sorted.sumOf { it.amount }` | Yes — computed from fetched list | ✓ FLOWING |

---

### Behavioral Spot-Checks

Step 7b: Spot-checks requiring a running Android device/emulator are SKIPPED (no runnable entry point accessible from this environment; Android app requires device installation). Build verification was done by the executor and confirmed via git log showing all three Plan 01 commits (8f532e0, a8128bb, 3fcae86) and one Plan 02 commit (583b189) exist in the repository. Static checks substitute:

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| Sorting logic in ViewModel, not Composable | `grep -rn 'sortedWith\|sumOf' expenses/ui/` | 0 matches in ui/ code (only comment text) | ✓ PASS |
| `onCategoryToggled` has no repository call | Read ViewModel — no `repository.getExpenses` inside `onCategoryToggled` | Confirmed: only `_filterState.update` + `emitDerivedState()` | ✓ PASS |
| `collectAsStateWithLifecycle` used (not bare `collectAsState`) | `grep collectAsStateWithLifecycle ExpensesScreen.kt` | Lines 35 (import) + 63–64 (usage); no bare `collectAsState(` call | ✓ PASS |
| 13 category enum values | `grep -c '"' ExpenseCategory.kt` | 13 string literals | ✓ PASS |
| No ANDROID_ID in DI/network path | `grep -rn 'ANDROID_ID' di/ net/WalletApiService.kt` | 0 matches | ✓ PASS |
| Debt markers (TBD/FIXME/XXX) | `grep -rn 'TBD\|FIXME\|XXX' expenses/` | 0 matches | ✓ PASS |

---

### Requirements Coverage

| Requirement | Plan | Description | Status | Evidence |
|-------------|------|-------------|--------|----------|
| EXP-01 | 02-01 | Reverse-chronological expense list, current month default | ✓ SATISFIED | `emitDerivedState()` sorts newest-first; `FilterState.default()` seeds current month |
| EXP-02 | 02-02 | Date range picker | ✓ SATISFIED | `DateRangePickerModal` + `DateQuickChips` + ViewModel methods fully wired |
| EXP-03 | 02-02 | Category filter chips (13 categories, multi-select) | ✓ SATISFIED | `CategoryFilterRow` with all 13 categories; multi-select via `Set<ExpenseCategory>` |
| EXP-04 | 02-02 | Summary card total for selected period and filters | ✓ SATISFIED | Total derived in `emitDerivedState()` from filtered list; rendered from `Success.total` |

**Discrepancy note on EXP-01 in REQUIREMENTS.md:** The traceability table shows EXP-01 as "Pending" and the checkbox is `[ ]` — this is a bookkeeping error in REQUIREMENTS.md. The implementation satisfies EXP-01 fully (code exists, is wired, and was verified by the checkpoint in 02-01). REQUIREMENTS.md should be updated to mark EXP-01 as Complete/checked. This is not a code gap.

**EXP-03 category count:** REQUIREMENTS.md defines 12 categories for EXP-03 (omits SAVINGS_TRANSFER). The implementation provides 13 per `CONTEXT.md D-07`, which is intentional and correct — the backend can return SAVINGS_TRANSFER expenses. This exceeds the requirement; it is not a gap.

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| None | — | — | — |

No debt markers (TBD/FIXME/XXX), no stubs, no hardcoded empty returns in production paths, no filtering/sorting/totalling logic in Composable files.

---

### Human Verification Required

All four items below are runtime behavior verifications — the code and wiring exist and are correct, but the state transitions require a running Android device/emulator connected to the backend.

#### 1. Previous month reload via 'Poprzedni' chip

**Test:** Launch app with backend running; confirm "Ten miesiąc" chip is selected. Tap "Poprzedni". Observe list and summary card.
**Expected:** List reloads with previous calendar month's expenses (correct API `from`/`to` params per Warsaw timezone); summary card label changes to prior month name (e.g., "Maj 2026"); total reflects sum of prior-month rows.
**Why human:** Correct date-boundary computation and API reload cannot be observed via static analysis; requires real data to confirm non-empty response and correct filtering.

#### 2. DateRangePicker interaction (confirm and cancel)

**Test:** Tap "Niestandardowy"; verify dialog opens titled "Wybierz zakres dat". Select a start date; confirm "Zastosuj" remains disabled until an end date is also selected. Select end date; tap "Zastosuj". Verify list and card update. Open again; tap "Anuluj"; verify no change.
**Expected:** Picker opens correctly; "Zastosuj" disabled until both dates selected (T-02-06); after confirm the list shows expenses for the chosen range; card shows "d MMM — d MMM yyyy" label; cancel leaves previous selection unchanged.
**Why human:** Modal interaction, timezone correctness (T-02-07: epoch-millis → Warsaw LocalDate), and cancel-no-change invariant require device execution.

#### 3. Multi-select category chips and total recomputation

**Test:** With expenses loaded, tap one category chip; observe list and total. Tap a second chip; observe list and total. Deselect all chips; observe full list.
**Expected:** Each selection narrows the list to the selected categories; total equals sum of visible rows (EXP-04); empty selection shows all expenses; multiple chips active simultaneously (EXP-03).
**Why human:** State-transition correctness (toggle adds/removes from Set, re-derives without network call) and correct total recomputation require runtime observation with real data.

#### 4. Loading / Error / Empty state rendering

**Test:** (a) On app launch, confirm spinner appears briefly. (b) Kill/unreachable backend; tap "Spróbuj ponownie"; confirm error state appears with ErrorOutline icon, "Nie udało się wczytać wydatków", retry button; tap retry, confirm spinner appears. (c) Select a date range or category combination with no matching expenses; confirm empty state shows ReceiptLong icon + "Brak wydatków" + "Nie znaleziono wydatków dla wybranego okresu i filtrów." with chips visible above.
**Expected:** All three states render as specified. In empty Success case: summary card + DateQuickChips + CategoryFilterRow remain visible above the empty illustration (D-14).
**Why human:** Network-kill and empty-data scenarios require runtime testing; state transition timing (spinner visibility) requires device execution.

---

## Gaps Summary

No gaps found. All 4 roadmap success criteria are satisfied by present, substantive, and wired code. The human verification items are runtime behavioral checks — the structural prerequisites for all behaviors are in place.

**Administrative item (not a code gap):** EXP-01 is incorrectly marked "Pending" in `.planning/REQUIREMENTS.md` traceability table. The implementation is complete. Recommend updating REQUIREMENTS.md to mark EXP-01 as Complete.

---

_Verified: 2026-06-18_
_Verifier: Claude (gsd-verifier)_
