# Research: Platform Architecture — Kotlin/Spring Boot + Compose Multiplatform

**Phase**: 0 — Unknowns resolution  
**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Date**: 2026-05-14

---

## R-001: Kotlin Version

**Decision**: Kotlin 2.x (2.0.x minimum, 2.1.x recommended as of 2026)  
**Rationale**: Kotlin 2.0 introduced the K2 compiler with significantly faster incremental builds and improved type inference. K2 is required for the latest Compose Multiplatform releases (1.7.x). The KMP plugin is fully stable under Kotlin 2.x. Kotlin 2.0+ is required for the `@Composable` compiler plugin to work on wasmJs targets.  
**Alternatives considered**: Kotlin 1.9.x — still receives patches but the Compose Multiplatform 1.7.x dependency tree requires Kotlin 2.0+; would require pinning to older Compose version and losing Wasm stability improvements.

---

## R-002: Spring Boot Version

**Decision**: Spring Boot 3.3.x (latest 3.x GA as of 2026)  
**Rationale**: Spring Boot 3.x requires JDK 17 minimum and targets JDK 21 LTS natively. It ships Spring Security 6.x (stateless JWT is a first-class supported pattern via `SecurityFilterChain` beans, no WebSecurityConfigurerAdapter). Spring Data JPA 3.x + Hibernate 6.x provide improved Kotlin-friendly APIs and Jakarta EE 10 namespace. Spring Boot Actuator 3.x provides `/actuator/health`, `/actuator/metrics` out of the box.  
**Alternatives considered**: Spring Boot 2.7.x — final patch release; lacks JDK 21 virtual threads support; Spring Security 5.x configuration is more verbose; Hibernate 5.x lacks Jakarta EE 10 namespace.

---

## R-003: JWT Library and Strategy

**Decision**: `io.jsonwebtoken:jjwt-api` (JJWT 0.12.x) for JWT creation/parsing in the backend; stateless token returned in response body, stored in-memory by the Ktor client.  
**Rationale**: JJWT is the de-facto standard Kotlin/Java JWT library with a fluent builder API. Stateless JWT with `Authorization: Bearer` header is the only viable strategy for Compose Multiplatform — there is no shared cross-platform cookie jar. Token stored in-memory (Kotlin `StateFlow`) means no XSS risk and natural expiry on app close/reload. Acceptable for the community portal use case.  
**Token structure**: `sub` = user UUID, `roles` = array of role names, `exp` = 1 hour, `iat` = issued-at. Short expiry is appropriate for an authenticated community portal; no refresh token complexity needed at MVP.  
**Alternatives considered**: Spring Security OAuth2 Resource Server — supports JWT natively via `spring-security-oauth2-resource-server`; considered, but adds unnecessary complexity (no authorization server needed for PPH); JJWT is sufficient and simpler. Refresh token rotation — rejected at MVP; token loss on page reload is acceptable for this user base.

---

## R-004: Compose Multiplatform Version and Web Maturity

**Decision**: Compose Multiplatform 1.7.x (latest stable/beta as of May 2026); Wasm target  
**Rationale**: JetBrains shipped Compose Multiplatform 1.6.x with Wasm as stable-in-preview in early 2025, followed by 1.7.x reaching Beta for Web (Wasm) target. iOS reached stable at 1.6.0 in 2025. For the PPH portal (behind-login, known user base, no SEO requirements), Beta/RC stability is acceptable. The Wasm target uses the same `commonMain` composables as Android and iOS — zero code duplication when mobile targets are added.  
**Known production constraints**:
- Initial bundle: ~4–6 MB Brotli-compressed; mitigated with a loading screen and CDN caching.
- Browser compatibility: Chrome 112+, Firefox 120+, Safari 17+ all support the required Wasm GC spec. A browser-version guard is recommended.
- Chrome DevTools Kotlin/Wasm debugger extension available (JetBrains plugin).  
**Alternatives considered**: Kotlin/JS (React wrapper via `kotlin-wrappers`) — shares Kotlin language but does NOT allow sharing Compose UI with Android/iOS; rejected because mobile code sharing is a stated requirement. Next.js (TypeScript) — originally planned in spec 001; rejected for this architecture because it cannot share code with the Kotlin backend or planned mobile app.

---

## R-005: Ktor Client Configuration

**Decision**: `io.ktor:ktor-client-core` (commonMain) + `io.ktor:ktor-client-js` (wasmJsMain engine) + `ktor-client-content-negotiation` + `ktor-serialization-kotlinx-json`  
**Rationale**: Ktor is the official JetBrains HTTP client with native KMP support. The `wasmJs` engine uses the browser `fetch` API under the hood but presents a Kotlin coroutine-based API to `commonMain`. The `ktor-client-auth` plugin handles Bearer token injection via an install block — the in-memory JWT is stored in a `StateFlow` in `AuthViewModel` and accessed by the Ktor `BearerAuthProvider`.  
**Base URL strategy**: `ApiClient.kt` in `commonMain` reads the backend base URL from `ApiConstants.BASE_URL` (defined in `shared/`). For development, this points to `http://localhost:8080/api/v1`. For production, it is injected at Wasm compile time via a Gradle property.  
**Alternatives considered**: `okhttp` — JVM/Android only, no wasmJs engine; not usable in commonMain. Raw JS `fetch` interop — would require `@JsExport` and `wasmJsMain`-only code; moves HTTP logic out of commonMain and prevents future Android/iOS sharing.

---

## R-006: Navigation Library

**Decision**: `org.jetbrains.androidx.navigation:navigation-compose` (Compose Multiplatform multiplatform edition, 2.8.x)  
**Rationale**: JetBrains maintains a KMP-compatible fork of Jetpack Compose Navigation that works in commonMain across Wasm, Android, and iOS. Type-safe `NavHost` with `@Serializable` route objects is available from 2.8.x. This is the lowest-risk navigation choice — same API as Android Jetpack Navigation, familiar to most Kotlin developers.  
**Alternatives considered**: Voyager (`cafe.adriel.voyager`) — popular KMP navigation library, independent of Jetpack; considered but JetBrains Navigation is now the official recommendation and avoids a third-party runtime dependency. Manual screen stacking — acceptable only for very small apps; rejected given the portal has 15+ screens.

---

## R-007: Kotlin Version Catalog (libs.versions.toml)

**Decision**: Gradle Version Catalog at `gradle/libs.versions.toml` with `[versions]`, `[libraries]`, `[plugins]` sections  
**Rationale**: Version catalogs are the official Gradle recommended approach since Gradle 7.4. All three subprojects reference the same catalog — a single location to update dependency versions. This is especially important for `shared/` which is consumed by both `backend/` and `frontend/` and must be on a consistent kotlinx version.  
**Key version pins**:
```toml
[versions]
kotlin = "2.1.0"
compose-multiplatform = "1.7.0"
spring-boot = "3.3.0"
spring-dependency-management = "1.1.4"
ktor = "3.0.0"
kotlinx-serialization = "1.7.0"
kotlinx-coroutines = "1.8.1"
jjwt = "0.12.6"
compose-navigation = "2.8.0"
```

---

## R-008: Dependency Injection (Backend)

**Decision**: Spring's built-in constructor injection (`@Service`, `@Repository`, `@RestController` annotations)  
**Rationale**: Spring Boot's application context provides DI out of the box. Kotlin data classes and constructor injection work cleanly together — no field injection, no `lateinit var` smell. No additional DI library needed.  
**Alternatives considered**: Koin — suitable for KMP shared modules (considered for `shared/` if it needed DI, which it does not); Spring context not available outside JVM target; concluded that `shared/` module does not need DI (pure functions and data classes only).

---

## R-009: State Management (Frontend)

**Decision**: `ViewModel` per screen using `lifecycle-viewmodel-compose` (KMP edition, 2.8.x) + Kotlin `StateFlow` for UI state  
**Rationale**: The JetBrains Lifecycle ViewModel KMP library (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`) is now stable and works in commonMain. Each screen has a corresponding ViewModel holding `StateFlow<ScreenState>`. This is the standard pattern from Android development, applied uniformly across Wasm and future mobile targets.  
**Alternatives considered**: Redux-style (Decompose, MVI) — more powerful for complex state machines; adds complexity not needed at portal scale. Plain `remember { mutableStateOf() }` — acceptable for simple screens but not testable independently of Compose; rejected for screens with async API calls.

---

## R-010: Backend API Error Handling

**Decision**: Global `@RestControllerAdvice` with a sealed `ApiError` response body; `HTTP 4xx/5xx` status codes; `Content-Type: application/json`  
**Rationale**: Centralised error handling ensures all error responses have the same shape — the shared module defines `ApiError` as a `@Serializable` data class that the Ktor client can deserialize on any platform. This eliminates platform-specific error parsing code.  
**ApiError shape** (in `shared/commonMain`):
```kotlin
@Serializable
data class ApiError(
    val code: String,       // machine-readable error code, e.g. "AUTH_INVALID_TOKEN"
    val message: String,    // human-readable message
    val field: String? = null  // for validation errors, the offending field
)
```

---

## R-011: Docker Image Strategy

**Decision**: Multi-stage Dockerfile — Stage 1: `gradle:8-jdk21` to build the fat JAR; Stage 2: `eclipse-temurin:21-jre-alpine` (or `gcr.io/distroless/java21`) as runtime image  
**Rationale**: Multi-stage builds keep the final image small (~200 MB with Alpine JRE vs ~800 MB with a full Gradle build image). The fat JAR approach (`bootJar` task in Spring Boot) produces a single self-contained artifact. Secrets are injected as environment variables at runtime — no secrets baked into the image.  
**Environment variables required** (documented in quickstart.md):
- `DATABASE_URL` — Neon PostgreSQL connection string
- `JWT_SECRET` — ≥256-bit random string for HMAC-SHA256 signing
- `JWT_EXPIRY_HOURS` — default `1`
- `CORS_ALLOWED_ORIGINS` — comma-separated list of frontend origins
- `MSG91_AUTH_KEY` — OTP provider
- `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` — payment provider

---

## R-012: Frontend Deployment (Vercel Static)

**Decision**: Vercel static site hosting; Wasm bundle produced by `./gradlew :frontend:wasmJsBrowserDistribution`; uploaded via Vercel CLI or GitHub Actions  
**Rationale**: Compose Multiplatform for Web produces a static bundle (HTML + JS + Wasm files). Vercel's static hosting is zero-configuration and globally CDN-distributed. The `vercel.json` at `frontend/` root configures the SPA fallback rewrite rule (all paths → `index.html`) needed for client-side Compose Navigation.  
**Alternatives considered**: Cloudflare Pages — equivalent feature set; either works; Vercel chosen for consistency with spec 001 deployment tooling. Netlify — equivalent; same reasoning.

---

## Unknowns Resolution Summary

All NEEDS CLARIFICATION items from the Technical Context are resolved:

| Item | Resolution |
|---|---|
| Kotlin version | 2.1.x (K2 compiler) |
| Compose Multiplatform version | 1.7.x (Wasm Beta, viable for PPH) |
| JWT library | JJWT 0.12.x |
| Ktor client engine | ktor-client-js (wasmJs) |
| Navigation | androidx navigation-compose KMP 2.8.x |
| State management | lifecycle-viewmodel-compose KMP + StateFlow |
| Error response shape | Shared `ApiError` @Serializable data class |
| Docker strategy | Multi-stage, Alpine JRE runtime |
| Frontend deploy | Vercel static (`wasmJsBrowserDistribution`) |
| Gradle versioning | `libs.versions.toml` version catalog |
