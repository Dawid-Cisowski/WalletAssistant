# Requirements: WalletAssistant Android

**Defined:** 2026-06-17
**Core Value:** Zawsze aktualny, zsynchronizowany obraz osobistych finansów dostępny na telefonie Android.

## v1 Requirements

### Authentication

- [ ] **AUTH-01**: App wysyła każdy request do backendu z podpisem HMAC-SHA256 (nagłówki X-Device-Id, X-Timestamp, X-Nonce, X-Signature) — identyczny schemat jak HealthAssistant
- [ ] **AUTH-02**: HMAC secret i device ID konfigurowane przez BuildConfig z local.properties (nigdy w git) — local.properties w .gitignore od pierwszego commita

### Expenses

- [ ] **EXP-01**: Użytkownik widzi listę wydatków w odwrotnej kolejności chronologicznej z domyślnym bieżącym miesiącem
- [ ] **EXP-02**: Użytkownik może wybrać zakres dat (date range picker)
- [ ] **EXP-03**: Użytkownik może filtrować wydatki według kategorii (chip filtry: FOOD_AND_DRINKS, TRANSPORT, SHOPPING, ENTERTAINMENT, SUBSCRIPTIONS, HEALTH, HOUSING, UTILITIES, EDUCATION, TRAVEL, BUSINESS, OTHER)
- [ ] **EXP-04**: Użytkownik widzi kartę z łączną sumą wydatków dla wybranego okresu

### Accounts

- [ ] **ACC-01**: Użytkownik widzi aktualne saldo dla każdego typu konta (BUSINESS, PERSONAL_SAVINGS, PERSONAL_SPENDING)
- [ ] **ACC-02**: Użytkownik widzi listę historycznych snapshotów salda

### Investments

- [ ] **INV-01**: Użytkownik widzi łączną wartość portfolio inwestycji
- [ ] **INV-02**: Użytkownik widzi podział portfolio według typu inwestycji (IKE, XTB_STOCKS, XTB_ETF, SAVINGS_ACCOUNT, CRYPTO, OTHER)

### App Shell

- [ ] **APP-01**: App ma dolny pasek nawigacji z 3 tabami: Expenses / Accounts / Investments
- [ ] **APP-02**: Każdy ekran obsługuje pull-to-refresh
- [ ] **APP-03**: Każdy ekran pokazuje stany: ładowanie (loading), błąd (error), brak danych (empty)
- [ ] **APP-04**: Kwoty walutowe wyświetlane w formacie polskim ("1 234,56 zł")

## v2 Requirements

### Charts & Analytics

- **CHART-01**: Wykres donut/słupkowy wydatków według kategorii
- **CHART-02**: Sparkline historii salda konta
- **CHART-03**: Wykres liniowy historii wartości portfolio
- **CHART-04**: Porównanie wydatków miesiąc do miesiąca

### UX Polish

- **UX-01**: Dark mode (Material3 dynamic color)
- **UX-02**: Procentowy udział per typ inwestycji

### Cloud Run Deploy

- **INFRA-01**: Backend wdrożony na Google Cloud Run z Dockerfile
- **INFRA-02**: Sekrety w Google Secret Manager (HMAC_DEVICES_JSON, DB credentials)
- **INFRA-03**: Cloud SQL z socket factory connector
- **INFRA-04**: Cloud Build CI/CD pipeline

## Out of Scope

| Feature | Reason |
|---------|--------|
| Dodawanie/edycja zdarzeń finansowych z telefonu | App jest read-only; zapis przez Claude AI via MCP |
| iOS | Tylko Android, instalacja lokalna APK |
| Multi-user | System osobisty, jeden device |
| Offline mode / Room DB caching | Dane zawsze pobierane z backendu; upraszcza app |
| Push notifications | Brak triggera; Claude AI nie generuje alertów w tym projekcie |
| Wyszukiwanie tekstowe | Brak full-text endpoint w backendzie |
| CSV/PDF export | Claude AI via MCP obsługuje eksport |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Pending |
| AUTH-02 | Phase 1 | Pending |
| EXP-01 | Phase 2 | Pending |
| EXP-02 | Phase 2 | Pending |
| EXP-03 | Phase 2 | Pending |
| EXP-04 | Phase 2 | Pending |
| ACC-01 | Phase 3 | Pending |
| ACC-02 | Phase 3 | Pending |
| INV-01 | Phase 3 | Pending |
| INV-02 | Phase 3 | Pending |
| APP-01 | Phase 4 | Pending |
| APP-02 | Phase 4 | Pending |
| APP-03 | Phase 4 | Pending |
| APP-04 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 14 total
- Mapped to phases: 14
- Unmapped: 0 ✓

---
*Requirements defined: 2026-06-17*
*Last updated: 2026-06-17 after initial definition*
