---
phase: "01-android-foundation"
plan: "01"
subsystem: "android-client"
status: complete
tags: [android, gradle, build-config, hmac, security, scaffold]
dependency_graph:
  requires: []
  provides: [android-project-skeleton, build-config-secret-injection]
  affects: [01-02-PLAN.md]
tech_stack:
  added:
    - "Android Gradle Plugin 8.13.0"
    - "Kotlin Android 1.9.20"
    - "Gradle 8.13 wrapper"
    - "OkHttp 4.12.0"
    - "Retrofit 2.9.0"
    - "Gson 2.10.1"
    - "kotlinx-coroutines-android 1.7.3"
    - "appcompat 1.7.0"
    - "lifecycle-runtime-ktx 2.8.0"
  patterns:
    - "BuildConfig secret injection from local.properties via java.util.Properties"
    - "Kotlin DSL Gradle build scripts (no Groovy)"
    - "Application subclass pattern (empty for Phase 1)"
key_files:
  created:
    - "~/AndroidStudioProjects/WalletAssistantAndroid/settings.gradle.kts"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/build.gradle.kts"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/gradle/wrapper/gradle-wrapper.properties"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/build.gradle.kts"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/.gitignore"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/local.properties.example"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/AndroidManifest.xml"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/WalletAssistantApp.kt"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/res/layout/activity_main.xml"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/res/values/strings.xml"
    - "~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/res/values/themes.xml"
  modified: []
decisions:
  - "Kotlin DSL (.kts) used for all build scripts per D-03 — diverges from HealthAssistant Groovy DSL"
  - "No product flavors (D-04) — single-flavor with debug/release build types; buildConfigField in both types"
  - "local.properties is .gitignored from the very first commit (D-06) — satisfies AUTH-02 and T-01-01"
  - "android:usesCleartextTraffic=true added to AndroidManifest for local HTTP dev (10.0.2.2:8080)"
  - "All library versions taken directly from HealthAssistant reference codebase — no version guessing"
metrics:
  duration: "2 minutes"
  completed_date: "2026-06-17"
  tasks_completed: 2
  tasks_total: 2
  files_created: 12
  files_modified: 0
---

# Phase 01 Plan 01: Android Project Scaffold Summary

**One-liner:** Kotlin DSL Android project skeleton with AGP 8.13.0/Gradle 8.13, `BuildConfig` secret injection from `local.properties` for BACKEND_URL/HMAC_SECRET/DEVICE_ID, and git baseline that excludes secrets from the first commit.

## What Was Built

A new standalone Android project at `~/AndroidStudioProjects/WalletAssistantAndroid/` (separate git repo). The project uses Kotlin DSL build scripts, targets compileSdk 35 / minSdk 26, and is ready for `./gradlew assembleDebug` once the developer populates `local.properties`.

The three HMAC credentials (`BACKEND_URL`, `HMAC_SECRET`, `DEVICE_ID`) are read from `local.properties` via `java.util.Properties` and injected as typed `BuildConfig` fields in both `debug` and `release` build types — with no hardcoded values in any committed file.

The first git commit in the Android repo was structured so that `.gitignore` was already present before any other files were staged, guaranteeing `local.properties` was never tracked.

## Tasks Completed

| # | Task | Commit (Android repo) | Files |
|---|------|-----------------------|-------|
| 1 | Scaffold project skeleton and Gradle build with secret injection | `23b015a` | settings.gradle.kts, build.gradle.kts, gradle-wrapper.properties, app/build.gradle.kts |
| 2 | App entrypoint, manifest, resources, and secret-safe git baseline | `ec4765f` | .gitignore, AndroidManifest.xml, WalletAssistantApp.kt, MainActivity.kt, activity_main.xml, strings.xml, themes.xml, local.properties.example |

## Deviations from Plan

None - plan executed exactly as written.

All dependency versions, SDK levels, and patterns were taken directly from the HealthAssistant reference codebase as specified in RESEARCH.md. The buildConfigField injection pattern was adapted from HealthAssistant's Groovy DSL to Kotlin DSL as specified in PATTERNS.md.

## Security Review (T-01-01)

Threat T-01-01 (Information Disclosure — local.properties) was mitigated:
- `.gitignore` contains `local.properties` as the first project file committed
- `git ls-files local.properties` returns empty — file is never tracked
- `local.properties.example` contains only placeholder values, no real secrets
- No hardcoded credential values appear in any `.kts` file

Threat T-01-02 (BuildConfig fields compiled into APK) accepted as per threat register — personal-use app, locally-installed only.

## Known Stubs

- `MainActivity.kt`: `setContentView` wired but no network call — Plan 02 will add the HMAC verification coroutine.
- `WalletAssistantApp.kt`: Empty Application class — Plan 02+ will add networking initialization if needed.
- `app/src/main/res/layout/activity_main.xml`: Single placeholder `TextView` — Plan 02+ will replace with real UI.

These stubs are intentional for the walking skeleton. Plan 02 wires the HMAC interceptor and verification call.

## Developer Action Required Before Build

Before running `./gradlew assembleDebug`:

1. Copy `local.properties.example` to `local.properties`
2. Set `sdk.dir` to your Android SDK path (Android Studio does this automatically)
3. Set `backend_url` (emulator: `http://10.0.2.2:8080`, physical device: LAN IP)
4. Generate HMAC secret: `openssl rand -base64 32` — set as `hmac_secret`
5. Set `device_id` (e.g. `wallet-android-device`)
6. Register the device in backend `HMAC_DEVICES_JSON` before running Plan 02 verification

## Self-Check: PASSED

- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/build.gradle.kts` exists with 6 buildConfigField entries
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/.gitignore` contains `local.properties`
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/AndroidManifest.xml` contains INTERNET permission and cleartext flag
- [x] `~/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt` defines `class MainActivity`
- [x] Commit `23b015a` exists in Android repo (Task 1)
- [x] Commit `ec4765f` exists in Android repo (Task 2)
- [x] `git ls-files local.properties` returns empty (UNTRACKED-OK verified)
