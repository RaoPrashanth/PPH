# Implementation Plan: Platform Architecture — Kotlin/Spring Boot + Compose Multiplatform

**Branch**: `002-kotlin-spring-kmp` | **Date**: 2026-05-14 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/002-kotlin-spring-kmp/spec.md`

## Summary

Establish the foundational project architecture for the PPH Digital Reception Portal using Kotlin throughout: a Spring Boot 3.x REST API in `backend/`, a Compose Multiplatform web app (Wasm target) in `frontend/`, and a shared KMP library in `shared/` consumed by both. All three are subprojects of a single Gradle multi-project build. The backend uses stateless JWT authentication, deploys via Docker to Railway/Render, and exposes all endpoints under `/api/v1/`. The frontend deploys as a static Wasm bundle to Vercel. The architecture accommodates future Android and iOS mobile targets with no structural changes.

## Technical Context

**Language/Version**: Kotlin 2.x / JVM 21 (LTS) — backend + shared (JVM target); Kotlin/Wasm — frontend (Wasm target)  
**Primary Dependencies**:
- **Backend**: Spring Boot 3.x, Spring Security (JWT / stateless), Spring Data JPA, Hibernate 6, Jackson (Kotlin module), Gradle 8.x Kotlin DSL
- **Frontend**: Compose Multiplatform 1.7.x (wasmJs target), Ktor Client (wasmJs engine), Compose Navigation (or Voyager), kotlinx.coroutines, kotlinx.serialization
- **Shared**: kotlinx.serialization (JSON), kotlinx.coroutines, kotlin-stdlib (commonMain)
- **Build**: Gradle 8.x, Kotlin DSL throughout; `settings.gradle.kts` at repo root declaring `backend`, `frontend`, `shared` subprojects

**Storage**: PostgreSQL 15 (Neon managed, same instance as spec 001); Spring Data JPA / Hibernate ORM; entities map to spec 001 data-model.md schema  
**Testing**: JUnit 5 + MockK + Spring Boot Test (backend); Kotlin Test + Compose UI Test (frontend); shared module tested on JVM target  
**Target Platform**: Backend → Linux container (Docker, Railway/Render PaaS); Frontend → Web/Wasm (Chrome, Firefox, Safari); Mobile → Android + iOS (future, source-set additions only)  
**Project Type**: Multi-module Gradle monorepo — web-service (backend) + web-app (frontend) + shared library  
**Performance Goals**: Backend ≤200 ms p95 for authenticated API calls; incremental build (single module change) ≤3 min; frontend initial Wasm load ≤6 MB compressed  
**Constraints**: All API endpoints under `/api/v1/`; no Groovy DSL in any build file; no direct database access from frontend; JWT stored in-memory only (no localStorage/cookies)  
**Scale/Scope**: ~3,000–5,000 resident users; 14 FR from spec 001 mapped to Kotlin backend; 4 development user stories (US1–US4)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Resident Value Gate**: ✅ PASS — Every module directly enables spec 001 resident workflows. `backend/` exposes the REST API for notice board, concierge, issue tracking, feedback, and RBAC. `frontend/` renders all resident-facing screens. `shared/` carries domain types that represent resident entities (User, NoticeItem, IssueTicket, etc.). No module exists for its own sake.

- **Privacy and Consent Gate**: ✅ PASS — JWT is stateless and stored in-memory only; no token persisted to localStorage or cookies. All personal data flows through typed, serialization-verified DTOs in `shared/commonMain`. Consent and notification preference fields (`notification_preferences`, `communication_consent`) are defined in shared DTOs, enforced on the backend via Spring Security, and surfaced to residents in the frontend UI. No untyped JSON crosses the API boundary.

- **Reliability Gate**: ✅ PASS — Spring Boot backend provides production-ready resilience primitives (retry templates, @Transactional, exception handlers). Typed `/api/v1/` contracts with kotlinx.serialization prevent silent deserialization failures — a contract mismatch is a compile error, not a runtime `null`. OTP/SMS delivery (MSG91/Twilio) is invoked only from the backend service layer; the frontend never calls notification providers directly, eliminating a whole class of delivery failure from the client.

- **Accessibility Gate**: ✅ PASS — Compose Multiplatform provides semantic composable structure and built-in accessibility semantics (Modifier.semantics, ContentDescription). US2 acceptance scenario explicitly requires keyboard-only navigation and usability at 375 px and 1280 px viewports. Accessibility checks are a required task type in spec 001 tasks.md (Phase 2 Foundational, T018).

- **Observability and Simplicity Gate**: ✅ PASS (with justified complexity) — Three modules is the minimum for multi-platform code sharing; a two-module approach (backend + frontend) would require duplicating all domain types. Spring Boot Actuator provides health, metrics, and info endpoints out of the box. All critical paths (auth, API calls, database) produce structured logs. The added build complexity of a Gradle multi-project structure is documented and justified in the Complexity Tracking table below.

## Project Structure

### Documentation (this feature)

```text
specs/002-kotlin-spring-kmp/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── api-contracts.md
│   └── build-contracts.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Gradle multi-project monorepo
settings.gradle.kts          # root; declares backend, frontend, shared subprojects
build.gradle.kts             # root; shared plugin versions via version catalog
gradle/
└── libs.versions.toml       # Gradle version catalog (all dependency versions)

backend/
├── build.gradle.kts         # Spring Boot, Spring Security, JPA, Kotlin JVM
├── Dockerfile               # multi-stage: gradle build → distroless JRE 21 image
└── src/
    ├── main/
    │   └── kotlin/
    │       └── com/pph/backend/
    │           ├── Application.kt          # @SpringBootApplication entry point
    │           ├── config/
    │           │   ├── SecurityConfig.kt   # Spring Security JWT filter chain
    │           │   ├── CorsConfig.kt       # CORS for frontend origins
    │           │   └── JwtConfig.kt        # JWT secret, expiry, filter
    │           ├── auth/
    │           │   ├── AuthController.kt   # /api/v1/auth/*
    │           │   ├── AuthService.kt
    │           │   └── JwtService.kt
    │           ├── users/
    │           │   ├── UserController.kt   # /api/v1/users/*
    │           │   ├── UserService.kt
    │           │   └── UserRepository.kt
    │           ├── notices/
    │           ├── bookings/
    │           ├── issues/
    │           ├── feedback/
    │           ├── forms/
    │           ├── audit/
    │           └── shared/                 # imports from :shared module
    └── test/
        └── kotlin/
            └── com/pph/backend/
                ├── auth/
                └── integration/

frontend/
├── build.gradle.kts                        # Compose Multiplatform, Ktor client, wasmJs target
└── composeApp/
    └── src/
        ├── commonMain/
        │   └── kotlin/
        │       └── com/pph/frontend/
        │           ├── App.kt              # root @Composable + navigation setup
        │           ├── navigation/
        │           │   └── NavGraph.kt     # Compose Navigation destination graph
        │           ├── auth/
        │           │   ├── LoginScreen.kt
        │           │   └── AuthViewModel.kt
        │           ├── notices/
        │           ├── concierge/
        │           ├── issues/
        │           ├── feedback/
        │           ├── admin/
        │           ├── network/
        │           │   └── ApiClient.kt    # Ktor client setup (base URL, auth header)
        │           └── theme/
        │               └── AppTheme.kt
        ├── wasmJsMain/
        │   └── kotlin/
        │       └── com/pph/frontend/
        │           ├── main.kt             # Wasm entry point
        │           └── interop/
        │               └── RazorpayBridge.kt  # JS interop for Razorpay SDK
        ├── androidMain/    # FUTURE — source set added when mobile app begins
        └── iosMain/        # FUTURE — source set added when mobile app begins

shared/
├── build.gradle.kts                        # KMP library: commonMain + jvmMain + wasmJsMain
└── src/
    ├── commonMain/
    │   └── kotlin/
    │       └── com/pph/shared/
    │           ├── dto/
    │           │   ├── UserDto.kt
    │           │   ├── NoticeDto.kt
    │           │   ├── BookingDto.kt
    │           │   ├── IssueDto.kt
    │           │   ├── FeedbackDto.kt
    │           │   └── AuditDto.kt
    │           ├── validation/
    │           │   └── Validators.kt       # shared validation functions
    │           └── constants/
    │               └── ApiConstants.kt     # base path, version, timeout values
    ├── jvmMain/        # JVM-specific actual implementations (if any)
    └── wasmJsMain/     # Wasm-specific actual implementations (if any)
```

**Structure Decision**: Multi-project Gradle monorepo (Option 2 extended with shared KMP module). `backend/` is a Spring Boot web service; `frontend/` is a Compose Multiplatform app; `shared/` is a KMP library depended upon by both. The `androidMain`/`iosMain` source sets are stubbed as comments now and activated with Gradle target declarations when mobile development begins — no structural change required at that point.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| 3 subprojects instead of 1 | `shared/` module must compile to both JVM (for backend) and Wasm (for frontend); a single-project layout cannot produce two different compilation targets from the same source | A 2-project layout (backend + frontend) would require duplicating all DTOs and validation logic, violating SC-005 and creating immediate type drift risk |
| Compose Multiplatform (Beta Web target) instead of React/Next.js | Code sharing with planned Android/iOS mobile app is a stated project requirement; no JS framework shares code with mobile | A React frontend cannot share types or business logic with a Kotlin backend or KMP mobile app without a separate code generation step |

