# Technology Stack

**Project:** WalletAssistant Android + Cloud Run Deploy
**Researched:** 2026-06-17
**Overall confidence:** MEDIUM (web-verified across official Android Developers and Google Cloud docs)

---

## Part 1: Android App Stack

### Core Android

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Kotlin | 2.1.21 | Primary language | Compose Compiler Gradle plugin works directly with Kotlin 2.0+; no separate `kotlinCompilerExtensionVersion` management needed |
| Android SDK | compileSdk 35, minSdk 26 | Build target | API 26 = Android 8.0, covers ~97% of active devices; API 35 required for latest Material3 |
| Jetpack Compose BOM | 2025.12.00 | UI toolkit | BOM 2025.12.00 is stable (Dec 2025); maps to Compose 1.10 + Material3 1.4.0; scroll performance now matches Views |
| Compose Material3 | 1.4.0 (via BOM) | Design system | Included in BOM 2025.12.00; Material You with dynamic color, ready-made finance-suitable components (cards, chips, scaffold) |
| AGP (Android Gradle Plugin) | 8.10.1 | Build tooling | Matches Android Studio Narwhal 2025.1.x; required for Kotlin 2.1 KSP processing |
| Gradle | 8.11.1 | Build system | Paired with AGP 8.10.1; Kotlin DSL |

### Dependency Injection

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Hilt | 2.59 | DI container | Compile-time DI with KSP; deep Jetpack integration (ViewModel injection, `@HiltAndroidApp`); Google's recommended approach for Android. For a personal app Koin would also work, but Hilt eliminates runtime resolution errors entirely and the compile-time safety matches the project's Java backend philosophy |
| KSP | 2.1.21-2.0.1 | Annotation processor | Replaces KAPT for Hilt; significantly faster incremental builds; required for Hilt 2.48+ |

**Not using Koin:** Koin uses runtime graph resolution which means DI errors surface at runtime, not build time. Hilt aligns with the broader project philosophy of "fail fast at compile time."

### Networking

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Retrofit | 3.0.0 | HTTP client / API layer | Retrofit 3.0.0 (stable May 2025) has native `suspend` function support — no `Call<T>` wrapper needed with coroutines; cleaner API definitions |
| OkHttp | 4.12.0 (bundled with Retrofit 3) | HTTP engine | Bundled in Retrofit 3; written in Kotlin; provides `Interceptor` interface used by HMAC signing |
| OkHttp Logging Interceptor | 4.12.0 | Debug logging | Same version as OkHttp; disabled in release builds via BuildConfig |
| Gson / Moshi | Moshi 1.15.2 | JSON serialization | Moshi with Kotlin codegen is preferred over Gson: null-safety, Kotlin data class support, no reflection; alternatives with Retrofit 3 converter |

**HMAC signing pattern:** Implement `okhttp3.Interceptor`, compute `HMAC-SHA256` via `javax.crypto.Mac` (available on all Android API levels), construct canonical string `METHOD\nPATH\nTIMESTAMP\nNONCE\nDEVICE_ID\nBODY`, base64-encode, inject `X-Device-Id`, `X-Timestamp`, `X-Nonce`, `X-Signature` headers. This is a 1:1 port of the existing `HmacUtil.java` from HealthAssistant.

### State Management

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| ViewModel (lifecycle-viewmodel) | 2.10.0 | Screen-level state holder | Standard Android; survives configuration changes; `viewModelScope` for coroutines |
| StateFlow (Kotlin stdlib) | Kotlin 2.1 | UI state stream | Hot flow, holds current state, perfect for UI; expose as `val uiState: StateFlow<UiState>` from private `MutableStateFlow` |
| lifecycle-runtime-compose | 2.10.0 | Lifecycle-aware collection | Provides `collectAsStateWithLifecycle()` — pauses collection in background, resumes on START; mandatory for battery/memory correctness |
| Kotlin Coroutines | 1.10.x | Async execution | `viewModelScope.launch { }` for data fetching; `Dispatchers.IO` for network |

**Architecture pattern:** MVVM with UiState sealed class. Each screen has one `ViewModel`, one `UiState` (sealed: `Loading`, `Success(data)`, `Error(message)`), exposed via `StateFlow`. No MVI overhead needed for a read-only viewer.

### Navigation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Navigation 3 (nav3) | 1.0.0 | Screen routing | Stable Nov 2025; built from scratch for Compose state; back-stack-first model; official successor to Navigation Compose (Nav2). New projects should use Nav3, not the older `navigation-compose` |

**Not using Nav2 (navigation-compose):** Nav2 was designed pre-Compose and bolted onto it. Nav3 is the proper Compose-native replacement and is now stable. For a 3-screen app (Expenses, Accounts, Investments) the migration cost is minimal.

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Coil | 3.x | Image loading (if needed) | Only if displaying chart images or remote assets; likely not needed for text-based finance data |
| Timber | 5.0.1 | Logging | Wraps Android Log; tag-based; easy no-op in release |
| DataStore Preferences | 1.1.x | Local config persistence | Store deviceId, backendUrl; NOT for secrets (use BuildConfig for HMAC secret at build time) |

---

## Part 2: Cloud Run Deploy Stack

### Containerisation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Docker multi-stage build | — | Container image | Stage 1: `eclipse-temurin:21-jdk` to build fat JAR; Stage 2: `eclipse-temurin:21-jre` runtime only. Keeps final image ~300MB vs ~800MB with full JDK |
| Layered JARs (Spring Boot) | Spring Boot 4.1 | JAR unpacking avoidance | Spring Boot fat JARs unpack on startup, adding ~2-3s cold start latency. Layered JARs let Docker cache dependencies layer separately and avoid runtime unpack |
| Google Artifact Registry | — | Container image storage | Replaces deprecated GCR (gcr.io); use `REGION-docker.pkg.dev/PROJECT/REPO/IMAGE:TAG` |

**Dockerfile pattern (layered JAR):**
```dockerfile
# Stage 1: Extract layers
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Add to `application.properties`:
```
server.port=${PORT:8080}
```

### CI/CD

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Google Cloud Build | — | CI/CD pipeline | Native GCP integration; `cloudbuild.yaml` in repo root; triggers on push via Cloud Build trigger; no external CI credentials needed |
| Cloud Build triggers | — | Automated deploy | Connect GitHub repo → Cloud Build trigger → auto-build + deploy on push to `main` |

**cloudbuild.yaml pattern:**
```yaml
steps:
  - name: 'gcr.io/cloud-builders/gradle'
    args: ['build', '-x', 'test']
  - name: 'gcr.io/cloud-builders/docker'
    args: ['build', '-t', '${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/wallet-assistant:$COMMIT_SHA', '.']
images:
  - '${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/wallet-assistant:$COMMIT_SHA'
substitutions:
  _REGION: europe-west1
  _REPO: wallet-assistant
```

### Secrets & Configuration

| Technology | Purpose | Pattern |
|------------|---------|---------|
| Google Secret Manager | Store `SPRING_DATASOURCE_PASSWORD`, `HMAC_DEVICES_JSON`, `API_KEY` | Reference secrets as env vars in Cloud Run service definition; pin to version number (not `latest`) for predictable startup |
| Cloud Run environment variables | Non-secret config | `SPRING_DATASOURCE_URL`, `MCP_BASE_URL`, `SPRING_PROFILES_ACTIVE=prod` set directly as Cloud Run env vars |
| IAM Service Account | Auth | Cloud Run service account needs `roles/secretmanager.secretAccessor` on each secret |

**Why not Spring Cloud GCP Secret Manager starter:** Adds `spring-cloud-gcp` dependency and `sm://` property prefix — unnecessary coupling. Cloud Run native env var injection is simpler and requires no code change in Spring Boot. The backend already reads secrets from env vars; this pattern continues unchanged.

### Cold Start Mitigation

| Strategy | Impact | Cost |
|----------|--------|------|
| `min-instances=1` | Eliminates scale-from-zero cold starts (most impactful for personal use) | ~$5-10/month for idle CPU+memory |
| Startup CPU boost | Up to 50% faster startup per Google data | No additional cost |
| Layered JARs | Removes 2-3s fat JAR unpack time | Build config change only |
| `spring.main.lazy-initialization=true` | Defers non-critical bean init | Test thoroughly; some beans require eager init |

**Recommendation for personal use:** Enable `min-instances=1` and startup CPU boost. Skip GraalVM native — adds significant build complexity (native-image compilation, reflection config) for a personal-use service where a 5-10s cold start on first daily request is acceptable.

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| DI | Hilt | Koin | Runtime graph resolution; errors at runtime not compile time |
| Navigation | Nav3 | Nav2 (navigation-compose) | Nav2 predates Compose; Nav3 is stable and purpose-built |
| HTTP | Retrofit 3 | Ktor Client | Retrofit is idiomatic for REST+Kotlin; team already familiar; Ktor better for multiplatform |
| JSON | Moshi | Gson | Gson uses reflection, no Kotlin null-safety; Moshi codegen is compile-time |
| State | StateFlow | LiveData | LiveData is Java-first, not idiomatic with Compose; StateFlow is the Kotlin successor |
| Container | Layered JAR | Jib | Jib also works well but requires Gradle plugin change; layered JAR keeps Dockerfile explicit and visible |
| Secrets | Cloud Run env var injection | Spring Cloud GCP sm:// | sm:// requires additional dependency and changes property syntax; native injection is zero-code-change |

---

## Full Dependency List (Android `build.gradle.kts`)

```kotlin
// Compose BOM
implementation(platform("androidx.compose:compose-bom:2025.12.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui-tooling-preview")
debugImplementation("androidx.compose.ui:ui-tooling")

// Activity + Lifecycle
implementation("androidx.activity:activity-compose:1.10.x")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

// Navigation 3
implementation("androidx.navigation3:navigation3-ui:1.0.0")
implementation("androidx.navigation3:navigation3-runtime:1.0.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.59")
ksp("com.google.dagger:hilt-compiler:2.59")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Networking
implementation("com.squareup.retrofit2:retrofit:3.0.0")
implementation("com.squareup.retrofit2:converter-moshi:3.0.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Moshi
implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.x")

// DataStore (for deviceId storage)
implementation("androidx.datastore:datastore-preferences:1.1.x")

// Logging
implementation("com.jakewharton.timber:timber:5.0.1")
```

---

## Sources

- [Jetpack Compose December '25 release (BOM 2025.12.00)](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html)
- [Compose to Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [Jetpack Navigation 3 stable announcement](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html)
- [Hilt vs Koin 2025: compile-time vs runtime DI](https://proandroiddev.com/hilt-vs-koin-the-hidden-cost-of-runtime-injection-and-why-compile-time-di-wins-3d8c522a073b)
- [Retrofit 3.0.0 release](https://github.com/square/retrofit/releases)
- [StateFlow and SharedFlow — Android Developers](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [lifecycle-runtime-compose 2.10.0](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [Optimize Java applications for Cloud Run](https://cloud.google.com/run/docs/tips/java)
- [Configure secrets for Cloud Run services](https://docs.cloud.google.com/run/docs/configuring/services/secrets)
- [Cloud Run cold start mitigation strategies](https://medium.com/google-cloud/3-solutions-to-mitigate-the-cold-starts-on-cloud-run-8c60f0ae7894)
- [Cloud Build: build and push Docker image](https://docs.cloud.google.com/build/docs/build-push-docker-image)
- [Hilt Gradle setup with KSP](https://dev.to/abdul_rehman_2050/how-to-properly-setup-hilt-in-android-jetpack-compose-project-in-2025-o56)
