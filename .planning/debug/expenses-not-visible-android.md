---
slug: expenses-not-visible-android
status: awaiting_human_verify
trigger: manual
goal: find_and_fix
created: 2026-06-18
updated: 2026-06-18
---

# Debug Session: expenses-not-visible-android

## Symptoms

- User added expenses via MCP (Claude AI tools)
- Expenses are NOT visible in the Android app
- Flow: MCP tool call → WalletEventsService → wallet_events table → @ApplicationModuleListener → ExpensesProjector → expense_projections table → GET /v1/expenses → Android app

## Current Focus

Fix implemented and verified by integration test suite. Awaiting human verification.

## Evidence

- timestamp: 2026-06-18
  checked: ApiKeyAuthenticationFilter
  found: Sets request.setAttribute("deviceId", appProperties.getApiKey().getDeviceId()) which defaults to "claude-ai"
  implication: MCP expenses stored with userId="claude-ai"

- timestamp: 2026-06-18
  checked: HmacAuthenticationFilter
  found: Sets request.setAttribute("deviceId", deviceId) where deviceId = X-Device-Id header
  implication: Android expenses read with userId=<android-device-id>

- timestamp: 2026-06-18
  checked: ExpensesController, AccountsController, InvestmentsController, WalletEventsController
  found: All called request.getAttribute("deviceId") to determine userId for data queries
  implication: Data isolated per deviceId — MCP and Android are different scopes → empty results

- timestamp: 2026-06-18
  checked: WalletTools.getDeviceId()
  found: Falls back to appProperties.getApiKey().getDeviceId() = "claude-ai" when transport context has no userId
  implication: MCP always uses "claude-ai" as userId

- timestamp: 2026-06-18
  checked: McpToolsConfiguration.contextExtractor
  found: Was reading "deviceId" attribute from servlet request — which equals "claude-ai" for API key auth
  implication: Even with contextExtractor, the value propagated was still "claude-ai"

- timestamp: 2026-06-18
  checked: Integration test run (28 tests)
  found: All pass after fix — userId attribute flows correctly through both auth paths
  implication: Fix is backward-compatible; when APP_OWNER_USER_ID is unset, behavior is same as before

## Eliminated

- hypothesis: Transport context extractor is not wired
  evidence: McpToolsConfiguration already correctly overrides WebMvcStreamableServerTransportProvider bean with proper contextExtractor. The real bug was that the underlying value read was still "claude-ai".
  timestamp: 2026-06-18

- hypothesis: Projection async pipeline is broken
  evidence: Events flow correctly through the pipeline. Data is written to DB but queried under wrong userId.
  timestamp: 2026-06-18

## Resolution

root_cause: |
  The system used deviceId as userId for data scoping. MCP (Claude AI) authenticates via
  API key → deviceId = "claude-ai" (default API_KEY_DEVICE_ID). Android authenticates via
  HMAC with its own X-Device-Id (e.g., "pixel-7"). These two values are different.
  ExpensesRepository.findByUserIdAndOccurredDateBetween("pixel-7", ...) returned empty
  results because all MCP expenses were stored under userId="claude-ai".

fix: |
  Introduced APP_OWNER_USER_ID env var and 'app.owner-user-id' config property in
  AppProperties. Both ApiKeyAuthenticationFilter and HmacAuthenticationFilter now set a
  'userId' request attribute (in addition to 'deviceId'). When APP_OWNER_USER_ID is
  configured, all devices use the same shared userId for data scoping — allowing MCP and
  Android to share the same data view. All 4 controllers and WalletTools updated to use
  'userId' attribute.

verification: |
  ./gradlew :integration-tests:test — all 28 tests pass.
  Backward compatible: when APP_OWNER_USER_ID is unset, userId = deviceId (existing behavior).
  To fix production: set APP_OWNER_USER_ID=<any-consistent-id> in the server environment,
  matching across all clients. Existing data stored under "claude-ai" userId would need
  a one-time DB migration to the new owner-user-id.

files_changed:
  - src/main/java/org/dawid/cisowski/walletassistant/config/AppProperties.java
  - src/main/resources/application.yml
  - src/main/java/org/dawid/cisowski/walletassistant/security/ApiKeyAuthenticationFilter.java
  - src/main/java/org/dawid/cisowski/walletassistant/security/HmacAuthenticationFilter.java
  - src/main/java/org/dawid/cisowski/walletassistant/expenses/ExpensesController.java
  - src/main/java/org/dawid/cisowski/walletassistant/accounts/AccountsController.java
  - src/main/java/org/dawid/cisowski/walletassistant/investments/InvestmentsController.java
  - src/main/java/org/dawid/cisowski/walletassistant/walletevents/WalletEventsController.java
  - src/main/java/org/dawid/cisowski/walletassistant/mcp/McpToolsConfiguration.java
  - src/main/java/org/dawid/cisowski/walletassistant/mcp/WalletTools.java
