---
status: resolved
phase: 02-expenses-screen
source: [02-VERIFICATION.md]
started: 2026-06-18T00:00:00Z
updated: 2026-06-18T00:00:00Z
---

## Current Test

number: 4
name: Trigger loading / error / empty states
expected: |
  Loading: centered spinner. Error: ErrorOutline icon + strings + retry button. Empty: ReceiptLong + "Brak wydatków" with chips still visible.
awaiting: complete

## Tests

### 1. Tap 'Poprzedni' chip and verify prior-month reload
expected: List reloads for previous calendar month; summary card label shows prior month name; total matches sum of visible rows
result: passed
notes: Verified via plan 02-02 Task 4 checkpoint — user approved 2026-06-18

### 2. Open DateRangePicker via 'Niestandardowy', select a range, confirm, then cancel
expected: After confirm: list shows chosen range, card shows 'd MMM — d MMM yyyy' label. After cancel: no change to selection
result: passed
notes: Verified via plan 02-02 Task 4 checkpoint — user approved 2026-06-18

### 3. Multi-select category chips and verify total recomputation
expected: Each chip combination narrows the visible list and the summary card total equals the sum of visible rows; deselecting all restores the full list
result: passed
notes: Verified via plan 02-02 Task 4 checkpoint — user approved 2026-06-18

### 4. Trigger loading / error / empty states
expected: Loading: centered spinner (no chips). Error: ErrorOutline icon + 'Nie udało się wczytać wydatków' + retry button. Empty: ReceiptLong icon + 'Brak wydatków' with chips still visible above.
result: passed
notes: Verified via plan 02-02 Task 4 checkpoint — user approved 2026-06-18

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
