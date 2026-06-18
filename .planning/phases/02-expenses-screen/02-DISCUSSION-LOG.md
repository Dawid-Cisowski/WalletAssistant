# Phase 2: Expenses Screen - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-18
**Phase:** 2-Expenses Screen
**Areas discussed:** Area selection (user delegated), MVVM+DI, Date picker UX, Category filter chips, Expense list item

---

## Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| MVVM + DI — architektura ekranu | Hilt vs. manual, StateFlow vs. LiveData, Repository layer | ✓ (auto) |
| Date picker UX | Quick chips vs. modal picker, display format | ✓ (auto) |
| Chipy kategorii — layout i zachowanie | 13 kategorii, LazyRow vs. FlowRow, selekcja | ✓ (auto) |
| Element listy wydatku — co pokazać | Pola, layout, kategoria display, total card | ✓ (auto) |

**User's choice:** "sam zdecyduj" — delegował wybór obszarów do Claude. Wszystkie 4 obszary wybrane.

---

## MVVM + DI — Architektura ekranu

### Pytanie 1: Jak tworzone są ViewModele?

| Option | Description | Selected |
|--------|-------------|----------|
| Hilt | @HiltViewModel + hiltViewModel(). Standard Compose, raz skonfigurowane dla wszystkich faz. | ✓ |
| Manual ViewModel factory | viewModels { } lambda. Mniej zależności, więcej boilerplate per ekran. | |

**User's choice:** Hilt (Recommended)

### Pytanie 2: UI state modeling

| Option | Description | Selected |
|--------|-------------|----------|
| StateFlow + sealed UiState | sealed class z Loading/Success/Error. Nowoczesny Compose pattern. | ✓ |
| LiveData + individual fields | Osobne LiveData per pole. Starszy pattern. | |
| Sam zdecyduj | Claude wybiera. | |

**User's choice:** StateFlow + sealed UiState (Recommended)

### Pytanie 3: Repository layer?

| Option | Description | Selected |
|--------|-------------|----------|
| Repository layer | ExpensesRepository opakowuje API. ViewModel nie zna Retrofit. | ✓ |
| API bezpośrednio z ViewModel | Mniej klas, wystarczające dla personal app. | |

**User's choice:** Repository layer (Recommended)

---

## Date Picker UX

### Pytanie 1: Jak użytkownik wybiera zakres dat?

| Option | Description | Selected |
|--------|-------------|----------|
| Quick chips + modal picker | "Ten miesiąc" / "Poprzedni" / "Niestandardowy" + DateRangePicker modal. | ✓ |
| Tylko Material3 DateRangePicker | Zawsze modal, brak quick chips. | |

**User's choice:** Quick chips + modal picker (Recommended)

### Pytanie 2: Wyświetlanie zakresu

| Option | Description | Selected |
|--------|-------------|----------|
| Tekst "1 cze — 18 cze 2026" | Kompaktowy chip/label z zakresem, tap otwiera picker. | ✓ |
| Dwa osobne pola From/To | Bardziej jawne, więcej miejsca. | |

**User's choice:** Tekst kompaktowy (Recommended)

---

## Chipy kategorii — layout i zachowanie

### Pytanie 1: SAVINGS_TRANSFER?

| Option | Description | Selected |
|--------|-------------|----------|
| Pokaż wszystkie 13 | Backend może zwrócić tę kategorię; ukrycie dezorientuje. | ✓ |
| Tylko 12 z REQUIREMENTS.md | SAVINGS_TRANSFER to wewnętrzny transfer. | |

**User's choice:** Tak — pokaż wszystkie 13 (Recommended)
**Notes:** Ważna rozbieżność: REQUIREMENTS.md ma 12 kategorii, backend 13 (SAVINGS_TRANSFER). User zdecydował pokazać wszystkie 13.

### Pytanie 2: Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Poziomy LazyRow ze scrollem | Standard Android (Google News). Nie zajmuje pionowego miejsca. | ✓ |
| FlowRow — zawijanie | Widoczne wszystkie bez scrollu, ale zajmuje dużo miejsca. | |

**User's choice:** Poziomy LazyRow ze scrollem (Recommended)

### Pytanie 3: Brak wybranych chipów

| Option | Description | Selected |
|--------|-------------|----------|
| Brak wybranych = pokaż wszystkie | Chipy jako zawężanie. Intuicyjne per EXP-03. | ✓ |
| Osobny chip "Wszystkie" | Explicit, ale dodatkowe miejsce. | |

**User's choice:** Brak wybranych = pokaż wszystkie (Recommended)

---

## Element listy wydatku

### Pytanie 1: Co pokazać w wierszu?

| Option | Description | Selected |
|--------|-------------|----------|
| Merchant + kwota + kategoria + data | Wiersz 1: merchant | kwota. Wiersz 2: kategoria | data. | |
| Description jako główne, merchant pomocniczo | Wiersz 1: description | kwota. Wiersz 2: merchant + kategoria | data. | ✓ |
| Sam zdecyduj | Claude wybiera. | |

**User's choice:** Description jako główne, merchant pomocniczo

### Pytanie 2: Wyświetlanie kategorii

| Option | Description | Selected |
|--------|-------------|----------|
| Polska nazwa tekstowa | "Jedzenie i napoje" z displayName(). Proste, czytelne. | ✓ |
| Kolorowy dot + angielska nazwa enum | Wymaga mappingu kolorów, mniej przyjazne. | |

**User's choice:** Polska nazwa tekstowa (Recommended)

### Pytanie 3: Total card — skąd brać sumę?

| Option | Description | Selected |
|--------|-------------|----------|
| Zawsze client-side | Suma z wyświetlanej listy (po filtrach). Spójna z UI. Brak dodatkowego API call. | ✓ |
| API /summary/monthly + client-side fallback | Dwie ścieżki kodu, większa złożoność, marginalna korzyść. | |

**User's choice:** Zawsze client-side z załadowanej listy (Recommended)

---

## Claude's Discretion

- Wybór obszarów do dyskusji — user delegował ("sam zdecyduj"), Claude wybrał wszystkie 4.
- Sekwencja i format pytań — Claude zdecydował samodzielnie.

## Deferred Ideas

- Kolorowe ikony/wskaźniki per kategoria — odłożone (nie wymagane w Phase 2)
- Polish currency format "1 234,56 zł" — Phase 4 (APP-04)
- Wykresy donut/słupkowe — v2 requirements
- Dark mode — v2 requirements
- Cloud Run deploy — deferred per STATE.md
