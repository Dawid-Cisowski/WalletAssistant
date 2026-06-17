# Feature Landscape

**Domain:** Personal finance viewer — Android, read-only, single-user, event-sourced backend
**Researched:** 2026-06-17
**Overall confidence:** MEDIUM (patterns from established apps cross-checked; specifics from web sources LOW individually)

---

## Table Stakes

Features that users expect as baseline. Absence makes the app feel broken or useless.

### Expense Views

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Chronological expense list | Primary reason to open the app; not having it = no product | Low | Reverse-chron default (most recent first); paginated or lazy-loaded via LazyColumn |
| Amount + date + category per row | Users scan the list to find specific transactions; missing any field forces memory | Low | Category shown as icon/chip, amount right-aligned |
| Monthly date range default | Finance is inherently monthly (salary cycles, budgets); defaulting to "this month" is universal expectation | Low | Pre-select current calendar month on screen open |
| Date range selector | Users need to audit past months; any fixed window without override is frustrating | Medium | Material3 `DateRangePicker` modal; quick-select chips: This month / Last month / Last 3 months |
| Category filter | 12 categories exist; users need to isolate e.g. SUBSCRIPTIONS or FOOD_AND_DRINKS | Low | Horizontal-scrollable chip row; "All" chip selected by default; single-select or multi-select |
| Category spending total | Comparing categories is the core insight; a list without totals forces manual arithmetic | Low | Total at top of filtered list, or in a summary card above the list |
| Total spend for selected period | The single number users check most; omitting it makes date filtering feel pointless | Low | Sticky summary card above list: "Total: 1 234,56 zł" |
| Loading indicator | Network requests to Cloud Run take 200-800 ms; blank screen = app broken impression | Low | `CircularProgressIndicator` centered while `UiState.Loading` |
| Empty state message | "No expenses in this period" must be explicit; empty list = bug impression | Low | Illustration or icon + one-line explanation |
| Error state with retry | Network failures are inevitable on mobile; no retry = user stuck, forces app restart | Low | Error card with message + "Retry" button wired to ViewModel refetch |

### Account Views

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Current balance per account | First thing users check; if not visible immediately, navigation feels wrong | Low | Card per account type (BUSINESS, PERSONAL_SAVINGS, PERSONAL_SPENDING) with latest snapshot value |
| Account type label | Three account types exist; unlabelled numbers are meaningless | Low | Display name mapping: BUSINESS → "Firmowe", PERSONAL_SAVINGS → "Oszczędności", PERSONAL_SPENDING → "Wydatkowe" |
| Balance history list | Users verify that a deposit or withdrawal is reflected; history is accountability | Low | Scrollable list of snapshots with date + balance; reverse-chron |
| Loading + error states | Same expectation as expense views | Low | Shared `UiState` sealed class pattern across all screens |

### Investment Views

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Total portfolio value | The number users want when they open the investments section | Low | Large headline number at top of screen; sum of all investment type snapshots |
| Breakdown by investment type | 6 types (IKE, XTB_STOCKS, XTB_ETF, SAVINGS_ACCOUNT, CRYPTO, OTHER); aggregated total without breakdown is insufficient for tracking | Low | List of cards, one per type, with current value and label |
| Investment type label | Raw enum names (XTB_ETF) are opaque without mapping | Low | Display map: IKE → "IKE", XTB_STOCKS → "XTB Akcje", XTB_ETF → "XTB ETF", SAVINGS_ACCOUNT → "Lokata/Konto oszczędnościowe", CRYPTO → "Krypto", OTHER → "Inne" |
| Loading + error states | Same baseline expectation | Low | Shared pattern |

### App-Level

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Bottom navigation (3 tabs) | Industry standard for 3-section apps; users expect tab bar for Expenses / Accounts / Investments | Low | Material3 `NavigationBar`; icons + labels for each section |
| Pull-to-refresh on all screens | Mobile convention for data-refresh; users trained by every other app | Low | Material3 `PullToRefreshBox` wrapping each screen's scrollable content |
| Consistent Material3 styling | Mismatched styling signals low quality; Material3 is the Android standard | Low | Single theme file; consistent color, typography, shape tokens |
| HMAC authentication (transparent) | Backend requires it; visible auth errors are confusing without clear messaging | Medium | Reuse `HmacUtil` from HealthAssistant; auth happens in OkHttp interceptor, invisible to user unless it fails |
| Network error distinct from auth error | "No connection" vs "Authentication failed" have different fixes; conflating them misleads the user | Low | Map `401/403` to "Authentication error — check device config"; map network timeout to "No connection — check internet" |

---

## Differentiators

Features that are not expected but add clear value when present.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Category spending chart (bar/donut) | Makes category comparison instant; replaces mentally comparing text numbers | Medium | Donut chart for category share of total spend; bar chart for month-over-month comparison. Use `Vico` or `MPAndroidChart` library. Only useful when ≥2 categories have data. |
| Balance history sparkline/line chart | Lets user see trend at a glance instead of reading rows; immediately shows if savings are growing | Medium | Line chart of balance snapshots over last N months. On account detail screen, above the history list. |
| Investment portfolio value history chart | The single most valuable insight for investments; shows growth trajectory | Medium | Line chart of total portfolio value over time using snapshot series. Most valuable differentiator in the investments section. |
| Per-account allocation percentage | Shows what fraction of total tracked money is in each account | Low | "23% of total" badge on each account card. Requires summing all accounts. |
| Investment type allocation percentage | Companion to total portfolio: how much is in risky (CRYPTO) vs safe (IKE) assets | Low | Percentage badge per investment type row. |
| Month-over-month expense comparison | Contextualizes current spending; "you spent 12% more than last month" is actionable | Medium | Secondary label under total spend card. Requires fetching prior period in parallel. |
| Category spending trend (last 3 months) | Users with regular subscriptions or habits want to see stability or creep | High | Requires multiple date-range queries and chart rendering per category. Defer past MVP. |
| Dark mode support | Expected by power users; Material3 makes it almost free with `dynamicColorScheme` | Low | Single `isSystemInDarkTheme()` branch in theme file; near-zero effort with Material3 dynamic color. |
| Polish locale formatting | Amounts displayed as "1 234,56 zł" match user's mental model; US format feels wrong for a PLN-denominated app | Low | `NumberFormat.getCurrencyInstance(Locale("pl", "PL"))`. Low effort, high polish. |

---

## Anti-Features

Things to explicitly NOT build in v1. Each has a clear reason and what to do instead.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Add / edit expense UI | Out of scope by design (PROJECT.md); adds auth complexity, form validation, error recovery | Data entry happens exclusively via Claude AI / MCP. Android is read-only. |
| Offline mode / local caching | PROJECT.md explicitly excludes it; adds Room DB, sync logic, cache invalidation, conflict resolution — 3x scope increase | Always fetch fresh from backend. Show pull-to-refresh. Accept that app requires network. |
| Budget setting and alerts | Requires write-side on Android, persistent local state, push notifications infrastructure | Not a viewer concern. If budgeting is needed, implement on backend and expose via GET endpoint in a future milestone. |
| Push notifications | Requires FCM integration, backend subscription registration, notification logic | Personal use, no SLA; user opens app when they want to check. |
| Multi-account aggregation / net worth | Tempting but net worth = summing accounts + investments; data model may not support it cleanly without a dedicated API endpoint | Add a `/v1/summary` backend endpoint in a future milestone if desired. Do not aggregate on client. |
| Transaction search (text) | Backend API has no full-text search endpoint; would require client-side filtering of full dataset download | Not feasible without backend support. Deferred. Category + date filter covers 90% of use cases. |
| Export to CSV / PDF | Personal use; user can query Claude AI for exports; Android PDF/CSV generation adds significant complexity | Claude AI via MCP already handles ad-hoc data export. |
| iOS version | PROJECT.md constraint: Android only | — |
| In-app authentication UI (login screen) | Single device, personal use; credentials configured at build time via `BuildConfig` | HMAC secret in `local.properties` at build time, never entered at runtime. |
| Biometric lock screen | Complexity disproportionate to risk for single-user personal app; backend HMAC already authenticates the device | Optional future hardening, not v1 scope. |

---

## Feature Dependencies

```
Bottom navigation (3 tabs)
  → Expenses tab
      → Expense list (table stakes)
          → Date range selector (table stakes)
          → Category filter chips (table stakes)
          → Category total summary card (table stakes)
          → Total spend card (table stakes)
      → Category chart (differentiator) ← requires expense list data
      → Month-over-month comparison (differentiator) ← requires 2x date-range fetch

  → Accounts tab
      → Account cards with current balance (table stakes)
      → Account detail screen
          → Balance history list (table stakes)
          → Balance history line chart (differentiator) ← requires history list data

  → Investments tab
      → Total portfolio value (table stakes)
      → Per-type breakdown list (table stakes)
      → Portfolio history chart (differentiator) ← requires snapshot series

HMAC auth interceptor (OkHttp)
  → All network calls on all screens (prerequisite for everything)

Loading / Error / Empty states
  → All screens (prerequisite for acceptable UX on any screen)

Pull-to-refresh
  → All screens (table stakes app-level, depends on loading state pattern)
```

---

## MVP Recommendation

Prioritize these in order for a shippable first milestone:

1. **HMAC auth OkHttp interceptor** — prerequisite; nothing works without it
2. **Bottom navigation shell** (3 tabs, empty screens) — structural foundation
3. **Shared UiState sealed class + Loading/Error/Empty composables** — reused on all 3 screens
4. **Expenses screen** — list + date range + category filter + totals (highest daily usage)
5. **Accounts screen** — balance cards + history list
6. **Investments screen** — total value + per-type breakdown list
7. **Pull-to-refresh** on all screens — last because it layers on top of working fetch

Defer to milestone 2:
- All charts (category donut, balance sparkline, portfolio history line) — differentiators, Medium complexity, require charting library integration
- Month-over-month comparison — requires parallel fetch logic
- Polish locale formatting — quick win, add to milestone 1 polish pass
- Dark mode — zero-effort with Material3, include in milestone 1 theme setup

---

## Sources

- [Best Personal Finance Software for Cash Flow and Expense Tracking (Quicken, 2026)](https://www.quicken.com/blog/best-personal-finance-software-for-cash-flow-and-expense-tracking/)
- [7 Best Personal Expense Tracker Apps of 2026 (NerdWallet)](https://www.nerdwallet.com/finance/learn/best-expense-tracker-apps)
- [Developing an Expense Tracking App: Pocket Planner Case Study (DEV Community)](https://dev.to/daviekim13/developing-an-expense-tracker-app-a-case-study-of-pocket-planner-1fdn)
- [Banking App Design: 10 Great Patterns (UX Paradise / Medium)](https://medium.com/uxparadise/banking-app-design-10-great-patterns-and-examples-de761af4b216)
- [Banking App Design: Principles, Examples and UX Best Practices (Purrweb, 2026)](https://www.purrweb.com/blog/banking-app-design/)
- [Pull to Refresh — Jetpack Compose (Android Developers)](https://developer.android.com/develop/ui/compose/components/pull-to-refresh)
- [Jetpack Compose Loading and Error States — Complete Guide (HandsOnAndroid)](https://handsonandroid.com/jetpack-compose-loading-error-handling/)
- [UI/UX Case Study: Investment Mobile App Design (Medium)](https://medium.com/@tepphy.juliana25/ui-ux-case-study-investment-mobile-app-design-9f73ee221bb)
- [Expense Tracker Android — open source reference (GitHub)](https://github.com/furqanullah717/expense-tracker-android)
