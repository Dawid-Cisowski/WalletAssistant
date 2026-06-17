# Phase 1: Android Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-17
**Phase:** 1-Android Foundation
**Areas discussed:** Package name, Language for new network code, Build flavors

---

## Package Name

| Option | Description | Selected |
|--------|-------------|----------|
| com.dawidcisowski.walletassistant | Based on name Dawid Cisowski | ✓ |
| com.cisowski.walletassistant | Shorter variant | |
| com.dawidcidowski.walletassistant | Based on home dir username | |

**User's choice:** `com.dawidcisowski.walletassistant`
**Notes:** User confirmed the full name-based package via free-text ("z dawid cisowski") and selection.

---

## Language for New Network Code

| Option | Description | Selected |
|--------|-------------|----------|
| net/ in Java, rest in Kotlin | Matches HealthAssistant pattern; Compose UI in Kotlin | ✓ |
| Whole app in Java | Conflicts with Jetpack Compose (Kotlin-only) | considered, rejected |

**User's choice:** Initially "whole app in Java" — revised to "net/ in Java, rest in Kotlin" after flagging that Jetpack Compose requires Kotlin for `@Composable` functions.
**Notes:** HmacUtil.java, HmacSigningInterceptor.java, WalletApiService.java → Java. MainActivity, ViewModels, Composables → Kotlin.

---

## Build Flavors

| Option | Description | Selected |
|--------|-------------|----------|
| local + production flavors | Like HealthAssistant — environment switching | |
| Single-flavor, local.properties | Only local dev config; all fields in local.properties | ✓ |

**User's choice:** "tylko local" — no product flavors.
**Notes:** Three fields in local.properties: `backend_url`, `hmac_secret`, `device_id`. All injected via `buildConfigField` into `BuildConfig`.

---

## Claude's Discretion

None — user made explicit decisions for all areas.

## Deferred Ideas

- Production flavor / Cloud Run URL — deferred to v2 (already in STATE.md deferred items)
