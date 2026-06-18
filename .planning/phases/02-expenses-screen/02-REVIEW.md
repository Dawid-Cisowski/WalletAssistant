---
phase: 02-expenses-screen
reviewed: 2026-06-18T12:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/di/NetworkModule.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/net/WalletApiService.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpenseDto.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpenseCategory.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesRepository.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesUiState.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesViewModel.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpensesScreen.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpenseListItem.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/build.gradle.kts
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/WalletAssistantApp.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/MainActivity.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/FilterState.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/DateQuickChips.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/DateRangePickerModal.kt
  - /Users/dawidcidowski/AndroidStudioProjects/WalletAssistantAndroid/app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/CategoryFilterRow.kt
findings:
  critical: 5
  warning: 5
  info: 3
  total: 13
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-06-18T12:00:00Z
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

This phase delivers the Android Expenses screen: Compose UI, ViewModel + FilterState, Retrofit network layer, and Hilt DI wiring. The overall architecture is sound — stateless composables, two-StateFlow pattern, and client-side category filtering are correctly implemented. However, five critical issues were found that each represent either a security vulnerability, a build-breaking missing file, or incorrect runtime behavior that will silently produce wrong results for users.

The most urgent issues are: (1) `HmacSigningInterceptor` is imported and used but the file does not exist in the repository — the project will not compile; (2) `usesCleartextTraffic="true"` is set globally in the manifest, allowing HTTP connections even to production; (3) HMAC secrets baked into the release APK's `BuildConfig` class are readable without root access; (4) `LocalDate.now()` in `ExpenseListItem` uses the device's default timezone instead of Warsaw, so date-display logic will misclassify dates around midnight on devices in other timezones; (5) the `onDismiss()` double-call in `DateRangePickerModal` dismisses the dialog twice on confirm.

---

## Critical Issues

### CR-01: `HmacSigningInterceptor` Does Not Exist — Build Fails

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/di/NetworkModule.kt:4-5`

**Issue:** `NetworkModule.kt` imports and instantiates `com.dawidcisowski.walletassistant.net.HmacSigningInterceptor`. No such file exists anywhere in the repository (confirmed by exhaustive `find` across the entire project). The project will not compile. Every network request also goes unsigned, defeating the backend's HMAC authentication completely.

**Fix:** Create `app/src/main/kotlin/com/dawidcisowski/walletassistant/net/HmacSigningInterceptor.kt` implementing `okhttp3.Interceptor`. The canonical string for HMAC-SHA256 signing (per backend CLAUDE.md) is `method\npath\ntimestamp\nnonce\ndeviceId\nbody`, with headers `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature`. The secret must be read from `BuildConfig.HMAC_SECRET`.

---

### CR-02: `usesCleartextTraffic="true"` Applied Globally — Security Risk for Production Builds

**File:** `app/src/main/AndroidManifest.xml:9`

**Issue:** `android:usesCleartextTraffic="true"` is set at the `<application>` level with no scope restriction. While justified for local development (`10.0.2.2:8080`), the flag applies equally to release builds, allowing the app to send HMAC-signed requests over plain HTTP to any host — including a misconfigured or future production URL. Since HMAC secrets are included in every request body/header, a network observer can replay requests within the 600-second nonce window.

**Fix:** Use a `network_security_config.xml` to restrict cleartext only to the emulator loopback, and apply it only in the debug build type:

```xml
<!-- res/xml/network_security_config_debug.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

Then in `AndroidManifest.xml` remove `usesCleartextTraffic="true"` and reference the config only in the debug manifest overlay (`src/debug/AndroidManifest.xml`).

---

### CR-03: HMAC Secret Embedded in Release APK `BuildConfig` — Credential Exposure

**File:** `app/build.gradle.kts:50-51`

**Issue:** `BuildConfig.HMAC_SECRET` is injected into the release build as a plain Java `String` constant. `BuildConfig` classes are compiled into the APK and are trivially extractable with `apktool` or any DEX decompiler — no root access or obfuscation bypass required. Even with ProGuard/R8 enabled, string constants in `BuildConfig` are not obfuscated. Anyone with the APK can extract the HMAC secret and forge arbitrary authenticated requests to the backend indefinitely (the backend has no mechanism to rotate secrets per-device without a code change).

Additionally, `isMinifyEnabled = false` in the release build type (line 52) means no code shrinking or obfuscation is applied at all, making extraction even easier.

**Fix (short-term):** Enable minification and resource shrinking for release:
```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

**Fix (proper):** Move the HMAC secret out of `BuildConfig` entirely. Store it in Android's `EncryptedSharedPreferences` (Jetpack Security), provisioned on first launch from a server-side key exchange or manual setup flow. `BuildConfig` is acceptable for non-secret configuration like `BACKEND_URL` and `DEVICE_ID`.

---

### CR-04: `LocalDate.now()` Uses Device Default Timezone in `ExpenseListItem` — Wrong Year Comparison

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpenseListItem.kt:51`

**Issue:** The date display logic determines whether to show `"d MMM"` vs `"d MMM yyyy"` by comparing `date.year == today.year`, where `today = LocalDate.now()` (line 51). `LocalDate.now()` without a `ZoneId` uses the device's default timezone. A device set to UTC will compute `today` as one day behind Warsaw time between midnight and 01:00 Warsaw (UTC+1) or between midnight and 02:00 in summer (UTC+2). This means an expense recorded today in Warsaw will display with the wrong format — `"d MMM yyyy"` instead of `"d MMM"` — for approximately one hour each night for any user whose device is not in the `Europe/Warsaw` timezone.

The rest of the codebase consistently uses `ZoneId.of("Europe/Warsaw")` for this exact reason (FilterState line 27, ExpensesViewModel line 64). This is an inconsistency in the same domain.

**Fix:**
```kotlin
val today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
```

---

### CR-05: `onDismiss()` Called Twice on Confirm — Double Dismiss

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/DateRangePickerModal.kt:49-50`

**Issue:** In the confirm button's `onClick`, when both dates are selected the code calls `onDateRangeConfirmed(from, to)` (line 49) and then `onDismiss()` (line 50). The `onDismiss` lambda in `ExpensesScreen.kt` sets `showPicker = false` (line 74). However, the `DatePickerDialog`'s `onDismissRequest` parameter is also wired to `onDismiss` (line 39). When the confirm button is tapped, `onDismiss()` is called explicitly inside `onClick`, and then `DatePickerDialog` may also call `onDismissRequest` as part of its own dismiss lifecycle. This results in `showPicker = false` being called twice in rapid succession.

More critically: `onDateRangeConfirmed` triggers `viewModel.onDateRangeSelected` which calls `loadExpenses()`, starting a coroutine and emitting `Loading` state. If the second `onDismiss()` triggers a recomposition before the first settles, the state is correct, but there is a race between the confirm callback and the dismiss. The correct pattern for `DatePickerDialog` is to let the dialog control its own dismiss via `onDismissRequest` — the confirm button's `onClick` should only call `onDateRangeConfirmed`, not `onDismiss`:

```kotlin
confirmButton = {
    Button(
        onClick = {
            val startMillis = state.selectedStartDateMillis
            val endMillis = state.selectedEndDateMillis
            if (startMillis != null && endMillis != null) {
                val warsawZone = ZoneId.of("Europe/Warsaw")
                val from = Instant.ofEpochMilli(startMillis).atZone(warsawZone).toLocalDate()
                val to = Instant.ofEpochMilli(endMillis).atZone(warsawZone).toLocalDate()
                onDateRangeConfirmed(from, to)
                // onDismiss() removed — onDateRangeConfirmed already triggers showPicker = false
                // in ExpensesScreen; dialog dismissed by onDismissRequest automatically
            }
        },
        enabled = bothSelected
    ) {
        Text("Zastosuj")
    }
}
```

Note: `onDateRangeConfirmed` in `ExpensesScreen` already calls `showPicker = false` on line 73, which removes the composable from the tree, closing the dialog. The explicit `onDismiss()` call on line 50 of `DateRangePickerModal` is therefore redundant and causes the double-dismiss.

---

## Warnings

### WR-01: Raw Exception Message Forwarded to UI — Exposes Internal Error Details

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesViewModel.kt:118-120`

**Issue:** The catch block passes `e.message` directly into the `Error` UI state:
```kotlin
_uiState.value = ExpensesUiState.Error(
    e.message ?: "Nie udało się pobrać wydatków"
)
```
Retrofit and OkHttp exception messages often contain internal URLs, stack fragments, or backend error bodies (e.g., `"Connect to 10.0.2.2:8080 [/10.0.2.2] failed: Connection refused"` or a 500 response body). This message is then rendered verbatim in the UI via `ExpensesScreen.kt` line 120 (the Text composable uses `state.message`... wait — actually `ExpensesScreen` shows a hardcoded string "Nie udało się wczytać wydatków", but the dynamic `state.message` from `ExpensesUiState.Error` is unused in the screen). The field exists but is never displayed — see WR-02. Regardless, storing a raw exception message in the public state model is poor practice and will become a leak if any future caller renders it.

**Fix:** Map to a user-friendly fixed string in the ViewModel:
```kotlin
_uiState.value = ExpensesUiState.Error("Nie udało się pobrać wydatków")
```

---

### WR-02: `ExpensesUiState.Error.message` Is Never Used in the UI

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesUiState.kt:18` and `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpensesScreen.kt:102-133`

**Issue:** `ExpensesUiState.Error` carries a `message: String` field. The error branch in `ExpensesScreen` (lines 102–133) displays hardcoded Polish strings and ignores `state.message` entirely. The field is dead — it is written by the ViewModel but never read. This creates a misleading contract: the error state appears to carry a contextual message, but the UI always shows a fixed string regardless of what the ViewModel puts there.

**Fix (option A):** Remove the `message` field from `ExpensesUiState.Error` since it is unused:
```kotlin
object Error : ExpensesUiState()
```

**Fix (option B):** Display `state.message` in the UI's secondary error text if contextual error messages are desired.

---

### WR-03: `ExpensesScreen` Shows Filters Only in `Success` State — Filters Invisible During Loading and Error

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpensesScreen.kt:90-199`

**Issue:** The `when (val state = uiState)` block in `ExpensesScreen` renders the `Loading` and `Error` states as full-screen overlays, completely replacing the layout. The `DateQuickChips` and `CategoryFilterRow` are only rendered inside the `is ExpensesUiState.Success` branch (lines 143–155). This means:

1. Tapping a different date chip while the previous load is still in progress shows a spinner with no chips visible — the user loses orientation.
2. In the `Error` state, the user cannot change the date range without first retrying — they must retry, wait for it to fail again, before they can adjust the filter that may have caused the error.

The design spec (D-14) says the summary card and chips should remain visible in all states. The current implementation contradicts this for Loading and Error.

**Fix:** Hoist the filter controls outside the `when` block so they always render. Move the `Loading`/`Error`/`Success` branching below the filter row:

```kotlin
Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    SummaryCard(total = if (uiState is ExpensesUiState.Success) uiState.total else 0.0, filterState = filterState)
    DateQuickChips(...)
    CategoryFilterRow(...)
    // Then branch on uiState for the list area only
    when (val state = uiState) { ... }
}
```

---

### WR-04: Sorting by `occurredDate` and `occurredAt` as Plain Strings — Lexicographic Sort Only Accidentally Correct

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesViewModel.kt:143-146`

**Issue:** The sort in `emitDerivedState()` uses:
```kotlin
compareByDescending<ExpenseDto> { it.occurredDate }
    .thenByDescending { it.occurredAt }
```
Both `occurredDate` (format `"yyyy-MM-dd"`) and `occurredAt` (ISO-8601 Instant, e.g. `"2026-06-18T14:30:00Z"`) are `String` fields in `ExpenseDto`. Lexicographic string comparison on ISO-8601 dates in descending order happens to be correct — the format is zero-padded and sortable as a string. However, this is fragile:

- `occurredAt` is documented as an Instant ISO string, but if the backend ever returns a non-UTC offset (e.g., `"2026-06-18T14:30:00+02:00"`) instead of `"Z"`, lexicographic sort will break (e.g., `"+02:00"` sorts differently from `"Z"`).
- There is no validation that `occurredDate` and `occurredAt` are in the expected format before sorting.

**Fix:** Parse before sorting. For `occurredDate` this is safe since the `ExpenseListItem` already wraps the parse in a try/catch showing the raw string on failure. For `occurredAt`, parse to `Instant`:
```kotlin
val sorted = filtered.sortedWith(
    compareByDescending<ExpenseDto> { runCatching { LocalDate.parse(it.occurredDate) }.getOrElse { LocalDate.MIN } }
        .thenByDescending { runCatching { Instant.parse(it.occurredAt) }.getOrElse { Instant.MIN } }
)
```

---

### WR-05: `BACKEND_URL` Falls Back to Empty String — Silent Misconfiguration at Runtime

**File:** `app/build.gradle.kts:43,49`

**Issue:** Both debug and release build types use `localProps["backend_url"] ?: ""`. If `local.properties` is missing or does not contain `backend_url`, `BuildConfig.BACKEND_URL` is set to the empty string `""`. In `NetworkModule.provideWalletApiService`, `rawUrl = BuildConfig.BACKEND_URL.trimEnd('/')` will be `""`, and Retrofit's `baseUrl("")` throws `IllegalArgumentException: baseUrl is empty` — but only at the point where Hilt first resolves the `WalletApiService` dependency, which happens on first use. The app will appear to launch normally and then crash when the first API call is attempted, with an unhelpful crash rather than a clear configuration error.

The same applies to `HMAC_SECRET` and `DEVICE_ID` (lines 44, 50), though empty secret/device-id will produce auth failures rather than crashes.

**Fix:** Add a Gradle task or check that fails the build when required properties are absent:
```kotlin
fun requiredProp(key: String): String =
    localProps.getProperty(key)
        ?: error("Missing required property '$key' in local.properties")

buildConfigField("String", "BACKEND_URL", "\"${requiredProp("backend_url")}\"")
buildConfigField("String", "HMAC_SECRET", "\"${requiredProp("hmac_secret")}\"")
buildConfigField("String", "DEVICE_ID",   "\"${requiredProp("device_id")}\"")
```

---

## Info

### IN-01: `Double` for Monetary Amounts — Floating-Point Precision Risk

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpenseDto.kt:3`, `ExpensesUiState.kt:16`

**Issue:** `amount: Double` and `total: Double` use IEEE 754 double precision. The comment in `ExpenseDto.kt` acknowledges this deferred to Phase 4. For display this is acceptable, but `sumOf { it.amount }` in `emitDerivedState()` will accumulate floating-point rounding errors visibly at amounts like `1.10 + 2.20 = 3.2999999999999998`. For a personal finance app this is visible to the user in the summary card.

**Fix:** The Phase 4 plan to upgrade to `BigDecimal` is correct. If earlier is possible, change `amount` to `String` in `ExpenseDto`, parse to `BigDecimal` in the ViewModel when computing totals, and format for display with `"%.2f".format(total)` or a `NumberFormat` instance.

---

### IN-02: `Locale("pl")` Constructor Deprecated — Use `Locale.forLanguageTag`

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ui/ExpensesScreen.kt:254`, `ExpenseListItem.kt:53,57`

**Issue:** `Locale("pl")` uses the deprecated single-argument `Locale` constructor. The preferred constructor since API 24+ is `Locale.forLanguageTag("pl")` or the Kotlin extension `java.util.Locale.of("pl")`. The deprecated constructor is functional but will produce lint warnings.

**Fix:**
```kotlin
val polishLocale = Locale.forLanguageTag("pl")
```

---

### IN-03: `ExpensesUiState` Uses Legacy `object` Syntax Instead of `data object`

**File:** `app/src/main/kotlin/com/dawidcisowski/walletassistant/expenses/ExpensesUiState.kt:11`

**Issue:** `object Loading : ExpensesUiState()` uses the pre-Kotlin 1.9 pattern for singleton sealed class members. Kotlin 1.9+ (used in this project — `kotlin-stdlib:1.9.20`) introduced `data object` which provides correct `toString()`, `equals()`, and `hashCode()` implementations for objects in sealed hierarchies. Using plain `object` means `Loading.toString()` returns an instance-address-based string rather than `"Loading"`, which makes debugging and logging harder.

**Fix:**
```kotlin
data object Loading : ExpensesUiState()
```

---

_Reviewed: 2026-06-18T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
