# Domain Pitfalls

**Domain:** Android personal finance app + Spring Boot on Google Cloud Run
**Researched:** 2026-06-17
**Overall confidence:** MEDIUM (cross-checked web sources)

---

## Critical Pitfalls

Mistakes that cause silent failures, rewrites, or hours of debugging.

---

### Pitfall 1: ANDROID_ID is Per-App Scoped — HealthAssistant's ID Will Not Work in WalletAssistant

**What goes wrong:**
`Settings.Secure.ANDROID_ID` has been scoped to the combination of (app signing key + user + device) since Android 8.0 (API 26). Two different apps on the same physical device see completely different values. WalletAssistant and HealthAssistant will each get their own distinct ANDROID_ID on the same phone.

**Why it happens:**
The PROJECT.md notes "reuse HmacUtil from HealthAssistant" and assumes the same deviceId approach works. It does — but the actual ANDROID_ID value will differ between the two apps because of Android's per-app scoping. If you manually copy the HealthAssistant ANDROID_ID into `HMAC_DEVICES_JSON`, that ID will NOT match what the WalletAssistant app produces.

**Consequences:**
- Every HMAC request from WalletAssistant fails with 401 or signature mismatch
- Failure is silent — no helpful error tells you it's an ID mismatch
- Takes time to diagnose because the signing code looks correct

**Prevention:**
1. On first app launch, log the device's ANDROID_ID to logcat (debug builds only)
2. Register the WalletAssistant-specific ANDROID_ID in `HMAC_DEVICES_JSON` as a separate entry (e.g., key `"wallet-android-device"`)
3. Never assume two apps on the same device share an ANDROID_ID

**Detection (warning signs):**
- Backend returns 401 or 403 on every request despite correct HMAC logic
- Comparing the deviceId header in server logs with the locally-logged ANDROID_ID shows a mismatch

**Phase:** Android HMAC auth setup (first Android phase)

---

### Pitfall 2: ANDROID_ID Changes After Factory Reset — HMAC Config Becomes Invalid

**What goes wrong:**
After a factory reset (or if the signing key changes during development), ANDROID_ID changes. The device secret registered in `HMAC_DEVICES_JSON` becomes stale. The app silently starts getting 401s with no obvious cause.

**Why it happens:**
ANDROID_ID persistence guarantee is: "stable across reinstalls IF package name and signing key are the same." Debug builds often use a different signing key than release builds. A factory reset always generates a new value.

**Consequences:**
- Need to re-register the device after every factory reset or debug/release key switch
- If discovered mid-development, requires a Cloud Run redeploy to update the env var

**Prevention:**
1. Use a fixed, stable device identifier for the single-device personal setup. Consider using a hardcoded `BuildConfig.DEVICE_ID` set in `local.properties` rather than the dynamic ANDROID_ID. This survives factory resets.
2. Alternatively, store the registered deviceId in `SharedPreferences` on first launch and never re-query ANDROID_ID thereafter.

**Detection (warning signs):**
- App worked before, then stops working after phone wipe or switching from debug to release APK

**Phase:** Android HMAC auth setup

---

### Pitfall 3: HMAC Base64 Encoding Mismatch Between Android and Backend

**What goes wrong:**
The backend's `HmacAuthenticationFilter` expects the signature in a specific Base64 format. Android's `Base64` utility has three relevant flags: `Base64.DEFAULT` (adds newline), `Base64.NO_WRAP` (no newline), and `Base64.URL_SAFE`. If the Android side uses `Base64.DEFAULT` but the backend uses standard Java `Base64.getEncoder()` without padding/newline, the signatures will never match.

**Why it happens:**
The `HmacUtil` from HealthAssistant uses `Base64.NO_WRAP` (correct), but if copied incorrectly or if a developer "simplifies" the encoding call, the newline character gets embedded in the header value. Some HTTP stacks silently strip this; others don't.

**Consequences:**
- HMAC verification fails on backend with no useful error message
- Debugging requires comparing raw header bytes, not just strings

**Prevention:**
- Always use `android.util.Base64.encodeToString(bytes, Base64.NO_WRAP)` — never `Base64.DEFAULT`
- Add a unit test that computes a known HMAC and asserts the exact string (no trailing `\n`)
- Keep the HmacUtil copy exactly as-is from HealthAssistant; don't "simplify" it

**Detection (warning signs):**
- HMAC works in unit tests but fails on real HTTP calls
- Server-side log shows signature length is 1-2 chars longer than expected (the `\n` character)

**Phase:** Android HMAC auth setup

---

### Pitfall 4: Timestamp Skew Causes Intermittent HMAC Failures

**What goes wrong:**
The backend's `HmacAuthenticationFilter` enforces a tolerance window (`HMAC_TOLERANCE_SEC`, default likely ±60s or ±300s). If the Android device clock drifts out of sync with the server clock, requests fail with timestamp-out-of-range errors. This happens silently in the background on low-end devices or after the phone wakes from deep sleep.

**Why it happens:**
Android devices use NTP sync, which can be delayed when on mobile data or immediately after wake-from-sleep. Cloud Run containers run on Google's NTP-synced infrastructure, so the mismatch is purely on the device side.

**Consequences:**
- Intermittent 401 failures that are impossible to reproduce in tests
- Retry logic amplifies the problem (nonce replay protection rejects retried requests with the same nonce)

**Prevention:**
1. Use `System.currentTimeMillis() / 1000` (epoch seconds) for the timestamp — do not use `LocalDateTime` formatted strings unless the backend is configured for that format
2. Verify the backend's canonical string format: from CLAUDE.md it is `METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY` — match this exactly
3. Set `HMAC_TOLERANCE_SEC` to at least 300 (5 minutes) in Cloud Run env vars to tolerate clock drift

**Detection (warning signs):**
- 401 errors that appear only on the physical device (not emulator), or disappear after reopening the app

**Phase:** Android HMAC auth setup

---

### Pitfall 5: Cloud Run Spring Boot Cold Start Takes 10-15 Seconds Without min-instances

**What goes wrong:**
Without `min-instances=1`, Cloud Run scales to zero after ~15 minutes of inactivity. The next request has to cold-start a Spring Boot + Spring Modulith JVM application, which takes 10-15 seconds. For a personal app, this means the first request of the day always times out or feels broken.

**Why it happens:**
JVM startup is expensive: class loading, Spring context initialization, Flyway migrations (if any pending), Hibernate validation, and Caffeine cache setup all happen before the first request is served. Cloud Run's default request timeout is 60s, so it does not hard-fail, but the UX is terrible.

**Consequences:**
- First request of the day returns in 10-15s (or times out on Android's OkHttp default 10s timeout)
- Flyway migration on startup adds 2-5s extra on first deploy after a schema change

**Prevention:**
1. Set `--min-instances=1` in the Cloud Run service definition — this is the simplest fix for a personal project (cost: ~$5-10/month on a tiny container)
2. Enable startup CPU boost: `--cpu-boost` flag or `run.googleapis.com/startup-cpu-boost: "true"` annotation — reduces startup by 30-50% at no extra cost
3. Increase OkHttp `connectTimeout` and `readTimeout` on the Android side to 30s to avoid false failures during cold starts

**Detection (warning signs):**
- First API call of the day consistently takes 10+ seconds
- Cloud Run metrics show instances scaling to 0 between uses

**Phase:** Cloud Run deploy

---

### Pitfall 6: HikariCP Default Pool Size (10) Multiplied by Cloud Run Instances Exceeds PostgreSQL max_connections

**What goes wrong:**
Cloud Run can spin up multiple instances under load. Each instance creates its own HikariCP pool with `max-pool-size=10` (the Spring Boot default). If Cloud Run scales to 10 instances (possible even for low traffic if Cloud Run decides to spread load), that is 100 connections — hitting PostgreSQL's default `max_connections=100` hard limit. New connections are rejected with "FATAL: sorry, too many clients already."

**Why it happens:**
The project's `STACK.md` shows `max-pool-size=10` (default). Cloud Run's default `--concurrency=80` means each instance handles up to 80 simultaneous requests. Combined with virtual threads (enabled in this project), the pool is a bottleneck at high concurrency, causing threads to queue, not the reverse — but instances can still multiply.

**Consequences:**
- Sporadic `HikariPool-1 - Connection is not available` exceptions
- These fail silently from the Android side as a 500 error

**Prevention:**
1. Reduce `spring.datasource.hikari.maximum-pool-size=2` for this personal project — 2 connections per instance is more than enough for read-only Android queries
2. Set `--max-instances=3` on the Cloud Run service to cap total connections at 6
3. Set `spring.datasource.hikari.keepalive-time=60000` (60s) — shorter than Cloud SQL's TCP timeout of 300s
4. Set `spring.datasource.hikari.idle-timeout=120000` (120s) to release idle connections quickly in a serverless environment

**Detection (warning signs):**
- `HikariPool-1 - Connection is not available, request timed out after 30000ms` in Cloud Run logs
- PostgreSQL logs show "too many clients"

**Phase:** Cloud Run deploy

---

### Pitfall 7: Cloud SQL Connection Requires Cloud SQL Auth Proxy or Connector — Plain JDBC URL Fails

**What goes wrong:**
You cannot connect to Cloud SQL from Cloud Run using a plain TCP JDBC URL (`jdbc:postgresql://HOST:5432/DB`) the same way you connect to a local Postgres. Cloud SQL requires either the Cloud SQL Auth Proxy sidecar or the Cloud SQL Connector (socket factory). Without it, the connection is either blocked by firewall or requires public IP exposure with TLS certificates.

**Why it happens:**
Cloud SQL instances sit in a separate VPC. Cloud Run to Cloud SQL connectivity needs explicit authorization via the Cloud SQL Admin API, and the recommended path uses the JDBC socket factory.

**Consequences:**
- `SPRING_DATASOURCE_URL` set to a plain IP fails with connection refused
- Even with public IP enabled, TLS certificate setup is error-prone

**Prevention:**
The recommended approach for Cloud Run + Spring Boot:
1. Add dependency: `com.google.cloud.sql:postgres-socket-factory`
2. Use JDBC URL: `jdbc:postgresql:///DB_NAME?cloudSqlInstance=PROJECT:REGION:INSTANCE&socketFactory=com.google.cloud.sql.postgres.SocketFactory`
3. Grant Cloud Run service account the `Cloud SQL Client` IAM role
4. Alternatively, use Spring Cloud GCP's `spring.cloud.gcp.sql.*` auto-configuration

**Detection (warning signs):**
- `Connection refused` or `Network is unreachable` in Cloud Run startup logs
- `javax.net.ssl.SSLException` when trying to connect to public IP

**Phase:** Cloud Run deploy

---

### Pitfall 8: Flyway Migration Runs on Every Cold Start — Race Condition With Multiple Instances

**What goes wrong:**
Flyway runs before the application accepts traffic. On Cloud Run, if two instances start simultaneously (e.g., on first deploy after a schema change), both attempt to acquire the Flyway migration lock on the database. One wins; the other fails with a lock timeout and the container crashes. Cloud Run then retries, causing a restart loop until the winning instance finishes and releases the lock.

**Why it happens:**
This is an inherent Flyway design issue when multiple app instances start concurrently. Spring Boot runs Flyway during `ApplicationContext` initialization, before health probes pass.

**Consequences:**
- New deployment with schema change fails to start for 2-5 minutes
- Cloud Run shows container crashes in metrics

**Prevention:**
1. For this personal project, `--max-instances=3` + `--min-instances=1` means only one instance typically starts fresh — the existing warm instance continues serving while the new one initializes
2. Set `spring.flyway.lock-retry-count=10` and `spring.flyway.lock-retry-wait=2s` to make retries more tolerant
3. For larger schema changes, set `--max-instances=1` temporarily during deploy

**Detection (warning signs):**
- Cloud Run shows container crash loops exactly on deploys with new Flyway migrations
- Logs show `Unable to acquire Flyway schema history table lock`

**Phase:** Cloud Run deploy

---

### Pitfall 9: Android cleartext HTTP Traffic Blocked on Android 9+

**What goes wrong:**
Android 9+ (API 28+) blocks all cleartext (HTTP, not HTTPS) traffic by default via the Network Security Config. Cloud Run URLs are always HTTPS (`https://your-service-xyz.a.run.app`), but during local development, if you configure `BuildConfig.BACKEND_URL=http://10.0.2.2:8080` for emulator testing, the app silently fails on Android 9+ without a helpful error message.

**Why it happens:**
`OkHttp` throws `java.io.IOException: CLEARTEXT communication not permitted` which surfaces as a generic network error in Retrofit. No warning appears in Android Studio logs unless you know to look for it.

**Consequences:**
- Local dev against emulator + local Spring Boot backend fails on modern API levels
- Easy to misdiagnose as an HMAC auth issue or server-side error

**Prevention:**
1. Use HTTPS even for local dev: Spring Boot supports self-signed certs or use a reverse proxy (ngrok)
2. If you need HTTP for local development, add `android:usesCleartextTraffic="true"` scoped only to the `debug` variant via a separate `network_security_config.xml` — never in the release variant
3. Cloud Run production URL is always HTTPS — no issue there

**Detection (warning signs):**
- Network calls fail only on Android 9+ physical devices or emulators
- Logcat shows `CLEARTEXT communication to 10.0.2.2 not permitted`

**Phase:** Android networking setup

---

### Pitfall 10: BuildConfig.HMAC_SECRET Readable in APK — But That's Acceptable for Personal Use

**What goes wrong:**
`BuildConfig.HMAC_SECRET` read from `local.properties` is compiled into the APK as a string literal. Anyone who decompiles the APK (e.g., via `jadx`) can extract the HMAC secret and forge requests to the backend.

**Why it happens:**
This is a fundamental limitation of the Android BuildConfig approach. The secret is in the binary.

**Consequences:**
- For a personal-use single-device app: acceptable risk. The APK is installed only on your device and not published to the Play Store.
- For any distributed app: critical vulnerability.

**Prevention:**
1. Do NOT commit `local.properties` to git (verify `.gitignore` includes it)
2. For a personal-only project: the BuildConfig approach is fine — document the acceptable risk in the project
3. If the app is ever distributed: move secret derivation to server-side; Android app provides a signed device attestation instead of a shared secret

**Detection (warning signs):**
- `local.properties` shows up in `git status` or `git diff` — add it to `.gitignore` immediately

**Phase:** Android project setup (Phase 1)

---

## Moderate Pitfalls

### Pitfall 11: Spring Boot 4 / Spring Security 7 CSRF Now Enabled for REST APIs

**What goes wrong:**
Spring Security 7 (ships with Spring Boot 4) enables CSRF by default for all endpoints, including REST APIs. Previously (Spring Boot 3 / Spring Security 6), stateless REST APIs could omit CSRF configuration entirely. In Boot 4, this silently causes all POST/PUT/DELETE requests to return 403.

**Prevention:**
The backend is already on Spring Boot 4.1 and presumably has a `SecurityFilterChain` configured. Verify it explicitly calls `csrf(csrf -> csrf.disable())` for the stateless REST API path. Also verify `authorizeRequests()` has been replaced by `authorizeHttpRequests()` — the old method is removed in Spring Security 7.

**Phase:** Backend already deployed — verify before Cloud Run deploy

---

### Pitfall 12: Cloud Run Health Check Must Pass Before Traffic Is Routed

**What goes wrong:**
Cloud Run's default health check hits `GET /` and expects a 200 response within the startup timeout. Spring Boot Actuator's health endpoint is at `/actuator/health`, not `/`. If no route is mapped to `/`, Cloud Run retries until timeout and marks the container unhealthy.

**Prevention:**
Configure Cloud Run to use Spring Boot Actuator's endpoint:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
startupProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  failureThreshold: 30
  periodSeconds: 5
```
Or ensure `/actuator/health` is the configured health check path in Cloud Run service definition.

**Phase:** Cloud Run deploy

---

### Pitfall 13: OkHttp Default 10s Read Timeout Fails During Cold Starts

**What goes wrong:**
OkHttp's default `readTimeout` is 10 seconds. A cold-start Spring Boot JVM on Cloud Run takes 10-15 seconds. The first request from the Android app to a cold instance reliably times out, showing a generic error to the user.

**Prevention:**
Configure OkHttp in the Retrofit builder:
```kotlin
OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
```
With `min-instances=1` this problem disappears, but set the timeout anyway as a safety net.

**Phase:** Android networking setup

---

### Pitfall 14: Cloud Run Secrets Must Use Secret Manager — Not Plain Env Vars in YAML

**What goes wrong:**
`HMAC_DEVICES_JSON` contains device secrets. Storing it as a plain environment variable in Cloud Run service YAML or in Cloud Build config files exposes the secret in plaintext in version control and in Cloud Build logs.

**Prevention:**
1. Store `HMAC_DEVICES_JSON`, `SPRING_DATASOURCE_PASSWORD`, and `API_KEY` in Google Cloud Secret Manager
2. Reference them in Cloud Run service definition as: `valueFrom.secretKeyRef`
3. Grant the Cloud Run service account `Secret Manager Secret Accessor` role
4. Never put actual secret values in `cloudbuild.yaml` or `service.yaml` committed to git

**Phase:** Cloud Run deploy

---

## Minor Pitfalls

### Pitfall 15: Nonce Cache TTL Mismatch Between Android and Backend

**What goes wrong:**
The backend's Caffeine nonce cache has `expireAfterWrite=600s` (10 minutes). If Android retries a failed request using the same nonce (e.g., due to a network timeout), the backend rejects it as a replay. OkHttp's built-in retry logic may trigger this.

**Prevention:**
Disable OkHttp's automatic retries: `OkHttpClient.Builder().retryOnConnectionFailure(false)`. Generate a fresh nonce (UUID) on every request attempt, including retries.

**Phase:** Android HMAC auth setup

---

### Pitfall 16: Spring Modulith Event Publication Log Holds Uncommitted Events Across Restarts

**What goes wrong:**
Spring Modulith's JDBC event store persists domain events that have not yet been published to listeners. On Cloud Run restart (scale-down + scale-up), outstanding events from `wallet_event_publications` table are replayed. For a personal project this is correct behavior, but if Flyway runs a migration that changes projection tables, stale events are replayed into the new schema and may fail.

**Prevention:**
Verify the `wallet_event_publications` table is empty before deploying schema-changing migrations. Add a check in the deploy runbook: query `SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL` — it should be 0 before deployment.

**Phase:** Cloud Run deploy with schema changes

---

### Pitfall 17: Android APK Signed With Debug Key — ANDROID_ID Differs From Release APK

**What goes wrong:**
ANDROID_ID is scoped to the signing key. Debug builds (signed with the Android debug keystore) and release builds (signed with your keystore) produce different ANDROID_IDs on the same device. If you register the debug ANDROID_ID in `HMAC_DEVICES_JSON` during development, the release APK will fail authentication.

**Prevention:**
During development, always test with a consistent identity strategy: either always use the debug APK and register its ID, or always use the release APK. Document which deviceId is registered for which build type in the project notes.

**Phase:** Android project setup

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Android HMAC setup | ANDROID_ID is per-app, not device-wide | Register WalletAssistant's specific ID separately from HealthAssistant |
| Android HMAC setup | Base64.DEFAULT adds newlines | Use `Base64.NO_WRAP` exclusively |
| Android HMAC setup | Nonce replay on OkHttp retry | Disable `retryOnConnectionFailure`, regenerate nonce per attempt |
| Android build setup | `local.properties` committed to git | Check `.gitignore` on day 1 |
| Cloud Run deploy | Cold starts 10-15s | Set `min-instances=1` + enable startup CPU boost |
| Cloud Run deploy | HikariCP pool × instances > max_connections | Set `maximum-pool-size=2`, `max-instances=3` |
| Cloud Run deploy | Cloud SQL plain JDBC fails | Use Cloud SQL socket factory connector |
| Cloud Run deploy | Secrets in env vars in git | Store all secrets in Secret Manager |
| Cloud Run deploy | Health check at `/` not at `/actuator/health` | Configure Cloud Run health probe path explicitly |
| Cloud Run deploy with migrations | Multiple instances race on Flyway lock | Set `max-instances=1` during deploys with schema changes |
| Android networking | HTTP cleartext blocked Android 9+ | Use HTTPS or scoped debug network config |
| Android networking | OkHttp 10s timeout < cold start | Increase timeouts to 30s |

---

## Sources

- [Android Developers Blog: Changes to Device Identifiers in Android O](https://android-developers.googleblog.com/2017/04/changes-to-device-identifiers-in.html) — MEDIUM confidence (cross-checked)
- [Google Cloud Run: Optimize Java applications](https://cloud.google.com/run/docs/tips/java) — MEDIUM confidence (cross-checked)
- [Cloud Run health checks documentation](https://docs.cloud.google.com/run/docs/configuring/healthchecks) — MEDIUM confidence
- [HikariCP + Cloud SQL for PostgreSQL](https://docs.cloud.google.com/sql/docs/postgres/samples/cloud-sql-postgres-servlet-limit) — MEDIUM confidence
- [Connect from Cloud Run to Cloud SQL PostgreSQL](https://cloud.google.com/sql/docs/postgres/connect-run) — MEDIUM confidence
- [Spring Boot 4 Breaking Changes — Java Code Geeks](https://www.javacodegeeks.com/2026/05/spring-boot-4-migration-breaking-changes-new-defaultsand-what-actually-broke.html) — MEDIUM confidence
- [Protecting secrets in Android — Lord Codes](https://www.lordcodes.com/articles/protecting-secrets-in-an-android-project/) — MEDIUM confidence
- [Android SSL Pinning — Ostorlab](https://blog.ostorlab.co/android-ssl-pinning.html) — LOW confidence (single source)
- [Spring Boot cold start latency for Cloud Run](https://oneuptime.com/blog/post/2026-02-17-how-to-optimize-cloud-run-cold-start-latency-for-java-and-spring-boot-applications/view) — MEDIUM confidence
