# Roadmap: WalletAssistant Android

## Overview

New Android app (Kotlin + Jetpack Compose) that gives read-only access to the existing WalletAssistant backend. Work follows strict build order: HMAC auth and networking come first (every API call depends on this), then the Expenses screen (establishes MVVM pattern), then Accounts and Investments screens (reuse the same pattern), and finally the navigation shell and polish that wires everything into a shippable APK.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Android Foundation** - New Android project with HMAC auth, OkHttp interceptor, Retrofit networking — verified against real backend
- [ ] **Phase 2: Expenses Screen** - Full expenses feature: reverse-chron list, date range picker, category filter chips, total spend card
- [ ] **Phase 3: Accounts + Investments Screens** - Account balances + history and investment portfolio views using the established MVVM pattern
- [ ] **Phase 4: Navigation Shell + Polish** - Bottom nav bar wiring all screens, pull-to-refresh, loading/error/empty states, Polish currency formatting

## Phase Details

### Phase 1: Android Foundation
**Goal**: Android project exists with working HMAC-signed requests to the WalletAssistant backend
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: AUTH-01, AUTH-02
**Success Criteria** (what must be TRUE):
  1. A signed HMAC request reaches the backend and returns HTTP 200 (not 401/403)
  2. HMAC secret and device ID come from `local.properties` via `BuildConfig` — never from hardcoded strings
  3. `local.properties` is listed in `.gitignore` from the first commit
  4. `HmacSigningInterceptor` is wired into `OkHttpClient` and signs every outgoing request automatically
**Plans**: TBD
**UI hint**: no

### Phase 2: Expenses Screen
**Goal**: User can browse and filter their expenses on the Expenses screen
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: EXP-01, EXP-02, EXP-03, EXP-04
**Success Criteria** (what must be TRUE):
  1. Expenses screen opens showing the current month's expenses in reverse-chronological order by default
  2. User can open a date range picker and expenses list updates to show the selected period
  3. User can tap category filter chips to narrow expenses; multiple chips can be active at once
  4. A summary card shows the total amount spent for the currently selected period and filters
**Plans**: TBD
**UI hint**: yes

### Phase 3: Accounts + Investments Screens
**Goal**: User can view account balances and investment portfolio on their respective screens
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: ACC-01, ACC-02, INV-01, INV-02
**Success Criteria** (what must be TRUE):
  1. Accounts screen shows the current balance for each account type (BUSINESS, PERSONAL_SAVINGS, PERSONAL_SPENDING)
  2. Accounts screen shows a list of historical balance snapshots below the current balances
  3. Investments screen shows the total portfolio value as a headline figure
  4. Investments screen shows the portfolio broken down by investment type (IKE, XTB_STOCKS, XTB_ETF, SAVINGS_ACCOUNT, CRYPTO, OTHER)
**Plans**: TBD
**UI hint**: yes

### Phase 4: Navigation Shell + Polish
**Goal**: All three screens are connected through a bottom navigation bar and the app handles every UI state correctly
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: APP-01, APP-02, APP-03, APP-04
**Success Criteria** (what must be TRUE):
  1. A bottom navigation bar with three tabs (Expenses / Accounts / Investments) is visible on every screen; tapping a tab switches screens with state preserved
  2. Pulling down on any screen triggers a data refresh
  3. Every screen shows a loading spinner while fetching, an error message with retry when the network fails, and an empty-state illustration when there is no data
  4. All monetary amounts display in Polish format ("1 234,56 zł")
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Android Foundation | 0/TBD | Not started | - |
| 2. Expenses Screen | 0/TBD | Not started | - |
| 3. Accounts + Investments Screens | 0/TBD | Not started | - |
| 4. Navigation Shell + Polish | 0/TBD | Not started | - |
