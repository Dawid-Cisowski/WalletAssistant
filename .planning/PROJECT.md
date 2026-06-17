# WalletAssistant

## What This Is

WalletAssistant to osobisty system zarządzania finansami składający się z backendu Spring Boot (event sourcing, modular monolith) oraz aplikacji Android do przeglądania danych. Backend przyjmuje zdarzenia finansowe przez REST API i projektuje je w denormalizowane widoki do odczytu. Aplikacja Android umożliwia podgląd wydatków, kont i inwestycji z telefonu.

## Core Value

Zawsze aktualny, zsynchronizowany obraz osobistych finansów — dostępny zarówno przez Claude AI (MCP) jak i przez aplikację Android.

## Requirements

### Validated

- ✓ Event ingestion via `POST /v1/wallet-events` — istniejący
- ✓ HMAC + API key authentication filter chain — istniejący
- ✓ Expense projections — query by date/category via `GET /v1/expenses` — istniejący
- ✓ Account balance snapshots — query via `GET /v1/accounts/*` — istniejący
- ✓ Investment snapshots — portfolio summary via `GET /v1/investments/*` — istniejący
- ✓ MCP integration — Spring AI tools exposed to Claude AI — istniejący
- ✓ Idempotent event ingestion — deduplikacja przez `idempotency_key` — istniejący
- ✓ Modulith event sourcing — projekcje odtwarzalne z `wallet_events` — istniejący

### Active

- [ ] Deploy backendu na Google Cloud Run (Dockerfile, Cloud Build, env vars z Secret Manager)
- [ ] Android app — widok wydatków (lista, filtrowanie po dacie/kategorii)
- [ ] Android app — widok kont (salda, historia snapshotów)
- [ ] Android app — widok inwestycji (portfolio, wartość po typach)
- [ ] Android HMAC auth — reuse HmacUtil z HealthAssistant, konfiguracja przez BuildConfig
- [ ] Android networking — Retrofit + OkHttp klient do WalletAssistant REST API

### Out of Scope

- Dodawanie zdarzeń finansowych z telefonu — aplikacja jest read-only; zapis przez Claude AI / MCP
- iOS — tylko Android, instalacja lokalna
- Multi-user — system osobisty, jeden device
- Web frontend — Claude AI jest interfejsem webowym; Android zastępuje potrzebę osobnego frontendu
- Offline mode / local caching na Android — dane zawsze pobierane z backendu

## Context

**Backend (istniejący, brownfield):**
- Spring Boot 4.1 + Java 21, Spring Modulith, PostgreSQL 16
- 7 modułów: `walletevents`, `expenses`, `accounts`, `investments`, `mcp`, `security`, `config`
- Auth: dwa filtry — `ApiKeyAuthenticationFilter` + `HmacAuthenticationFilter`
- HMAC canonical string: `METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY`
- Device secrets z env var `HMAC_DEVICES_JSON` (JSON map deviceId → secret)
- MCP SSE endpoint na `/sse`, forwarded do `/mcp`

**Aplikacja Android (nowy komponent):**
- Wzorzec z HealthAssistant (`/AndroidStudioProjects/HealthAssistant/`) — identyczny schemat HMAC
- `HmacUtil.java` z HealthAssistant do przeniesienia 1:1
- Jetpack Compose + Kotlin, Retrofit + OkHttp
- `BuildConfig.BACKEND_URL` i `BuildConfig.HMAC_SECRET` z `local.properties`
- Osobne repo: `~/AndroidStudioProjects/WalletAssistantAndroid/` (nowy katalog)

**Deploy:**
- Backend → Google Cloud Run (tak jak HealthAssistantServer)
- Android → instalacja lokalna APK na telefon

## Constraints

- **Tech (backend)**: Java 21 + Spring Boot 4.1 — nie zmieniamy stacku backendu
- **Auth**: HMAC-SHA256 z nagłówkami `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature` — Android musi używać tego samego schematu co backend
- **Mobile**: Android only, Jetpack Compose — nie cross-platform
- **Repo**: Osobne repo dla Android — nie monorepo z backendem (zgodnie z wzorcem HealthAssistant)
- **Deploy**: Google Cloud Run — backend musi mieć Dockerfile i Cloud Build config
- **Personal use**: Brak SLA, brak rate limitingu, uproszczona obsługa błędów

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Osobne repo Android | Oddzielenie technologii Android od Spring Boot; zgodność z wzorcem HealthAssistant | — Pending |
| Reuse HmacUtil z HealthAssistant | Już działający, przetestowany kod; ten sam schemat HMAC co backend | — Pending |
| Android read-only | Zapis przez Claude AI / MCP jest wystarczający; upraszcza app mobilną | — Pending |
| Google Cloud Run dla backendu | Ten sam stack co HealthAssistantServer; sprawdzone w praktyce | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-17 after initialization*
