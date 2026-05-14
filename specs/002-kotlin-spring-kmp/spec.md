# Feature Specification: Platform Architecture — Kotlin/Spring Boot Backend and Compose Multiplatform Frontend

**Feature Branch**: `[002-kotlin-spring-kmp]`  
**Created**: 2026-05-14  
**Status**: Draft  
**Input**: User description: "Prepare the backend in Kotlin and Spring Boot framework and shall be in a folder and keep the frontend web app in separate folder. In future we may need Mobile app and I may use Kotlin Multiplatform so, if the Kotlin Multiplatform is already matured enough then use this for web app too."

## KMP Web Maturity Assessment (2026)

**Decision: Yes — use Compose Multiplatform for the frontend.**

As of 2026, JetBrains Compose Multiplatform covers four targets from a single codebase:

| Target | Status (2026) | PPH Suitability |
|---|---|---|
| JVM / Desktop | Stable | N/A for PPH |
| Android | Stable | ✅ Future mobile |
| iOS | Stable (reached 1.0 in 2025) | ✅ Future mobile |
| Web (Wasm) | Beta → approaching stable | ✅ Viable for PPH |

**Why Compose Multiplatform Web is viable for PPH specifically**:

1. The portal is entirely behind authentication — no SEO requirements (the main objection to Wasm-based web apps).
2. Residents are a defined, onboarded user base (~3,000–5,000 users) on modern browsers — not a general public audience.
3. Choosing Compose Multiplatform now means the future mobile app (Android + iOS) shares UI components, navigation, and business logic without a full frontend rewrite.
4. Kotlin is already selected for the backend — a single language across the full stack reduces team context switching.

**Known trade-offs (documented as assumptions)**:

- Initial Wasm bundle is larger (~4–6 MB compressed) than a React SPA; a loading screen is required on first visit.
- Browser DevTools support for Kotlin/Wasm is functional but less mature than Chrome DevTools for JavaScript.
- Some browser-native APIs (file picker, camera) require explicit JavaScript interop wrappers.
- Compose Multiplatform Web ecosystem has fewer third-party UI libraries than React; custom components are the norm.

---

## Clarifications

### Session 2026-05-14

- Q: What authentication token strategy should the Spring Security backend use? → A: JWT (stateless) — frontend stores token in-memory; `Authorization: Bearer` header; no server-side session state; works natively on all KMP targets (Wasm, Android, iOS).
- Q: What is the backend hosting environment? → A: PaaS (Railway or Render) via Docker container — Spring Boot packaged as a Docker image; environment variables managed in PaaS dashboard; connects to Neon PostgreSQL via `DATABASE_URL`.
- Q: What API versioning strategy should be used? → A: URL path prefix — all endpoints under `/api/v1/`; prefix configured once as the Ktor client base URL in the shared module; breaking changes introduce `/api/v2/` alongside `/api/v1/` temporarily.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Operational Kotlin/Spring Boot Backend (Priority: P1)

As a developer, I want a running Spring Boot REST API in the `backend/` folder so that the frontend can be connected and all feature development can proceed against a real backend.

**Why this priority**: The backend is the foundation all feature APIs depend on. Nothing else can be built or tested until the backend is operational and serving authenticated responses.

**Independent Test**: Can be fully tested by starting the backend (`./gradlew bootRun`) and calling at least one authenticated REST endpoint (e.g., `GET /api/health` and `POST /api/auth/otp/request`) from a REST client. Confirms: Spring Boot starts, security config loads, CORS is configured for the frontend origin, and the database connection is live.

**Acceptance Scenarios**:

1. **Given** a developer clones the repository and configures environment variables, **When** they run `./gradlew :backend:bootRun`, **Then** the Spring Boot application starts without errors and the health endpoint returns 200.
2. **Given** the backend is running, **When** an unauthenticated request is made to a protected endpoint, **Then** the server returns 401 with a structured error body.
3. **Given** valid credentials are supplied, **When** the authentication endpoint is called, **Then** a session token is returned and subsequent requests with that token succeed.
4. **Given** the backend is running, **When** the frontend origin makes a cross-origin request, **Then** CORS headers are present and the request is not blocked.

---

### User Story 2 — Operational Compose Multiplatform Web Frontend (Priority: P1)

As a developer, I want a running Compose Multiplatform web app in the `frontend/` folder that loads in a browser and reaches the backend, so that resident-facing UI development can begin.

**Why this priority**: The frontend is the resident's interface. Confirming it loads, connects to the backend, and renders basic navigation unblocks all resident-facing feature work.

**Independent Test**: Can be fully tested by running `./gradlew :frontend:wasmJsBrowserDevelopmentRun` and opening the resulting URL in a browser. Confirms: Wasm bundle loads, application starts, login page renders, and an OTP request to the live backend completes.

**Acceptance Scenarios**:

1. **Given** a developer runs the frontend development server, **When** they open the application in a modern browser, **Then** the Compose Multiplatform web app renders the login/home screen without console errors.
2. **Given** the frontend is open in a browser, **When** a user submits the login form, **Then** the request reaches the backend API and returns a valid response.
3. **Given** the app is open on a mobile-width viewport (375 px) and a desktop-width viewport (1280 px), **When** the user navigates any screen, **Then** content is fully usable on both without horizontal scrolling or hidden elements.
4. **Given** a keyboard-only user navigates the app, **When** they tab through interactive elements, **Then** focus is visible and all actions are reachable without a mouse.

---

### User Story 3 — Shared KMP Domain Module (Priority: P2)

As a developer, I want a shared Kotlin Multiplatform module in `shared/` that is used by both the backend and the frontend, so that domain models, DTOs, and validation rules are defined once and type-safe on all platforms.

**Why this priority**: Without the shared module, data contracts are duplicated between backend (Kotlin/JVM) and frontend (Kotlin/Wasm), creating drift and bugs. Sharing them is a prerequisite for confident feature development across both codebases.

**Independent Test**: Can be fully tested by compiling `./gradlew :shared:compileKotlinJvm` (for backend use) and `./gradlew :shared:compileKotlinWasmJs` (for frontend use) without errors. A type used in the shared module must be importable and usable in both `backend/` and `frontend/` source files.

**Acceptance Scenarios**:

1. **Given** a domain model (e.g., `UserDto`) is defined in `shared/src/commonMain`, **When** the backend imports and uses it in an API response body, **Then** the code compiles and serializes correctly.
2. **Given** the same `UserDto` is imported in the Compose Multiplatform frontend, **When** the frontend deserializes the backend API response, **Then** the fields are accessible with the same names and types without a separate mapping step.
3. **Given** a validation function is defined in the shared module, **When** it is called from both backend and frontend code, **Then** identical input produces identical output on both platforms.

---

### User Story 4 — Mobile-Ready Architecture Validated (Priority: P2)

As a developer, I want confirmation that adding an Android or iOS Compose Multiplatform target to the existing `frontend/` project requires only configuration additions — not architectural restructuring — so that the future mobile app can be delivered without significant rework.

**Why this priority**: The architecture must accommodate mobile expansion from the start. Validating this before significant feature development avoids expensive refactoring later.

**Independent Test**: Can be fully tested by adding the `androidMain` source set and a minimal Android target to `frontend/composeApp/build.gradle.kts` and confirming the shared screens compile for both `wasmJs` and `android` targets without modifications to the existing screen code.

**Acceptance Scenarios**:

1. **Given** the frontend project uses commonMain for all screens, **When** an `androidMain` target is added to the build file, **Then** the existing screens compile for Android without modification.
2. **Given** an `iosMain` target is similarly added, **When** the shared screens compile, **Then** iOS-specific code is isolated to `iosMain` source sets and does not pollute shared code.
3. **Given** a developer reviews the project structure, **When** they assess the effort to add a new mobile platform, **Then** the effort is limited to target configuration and platform-specific entry point files only.

### Edge Cases

- The Wasm bundle fails to load on an older browser that does not support the required Wasm features; the app must show a clear unsupported-browser message rather than a blank screen.
- A breaking change in a shared module type is applied; both backend and frontend compilation must fail immediately, preventing a runtime type mismatch from being deployed.
- The frontend dev server and backend run on different ports; CORS must be configured correctly for local development and production origins.
- A developer adds a platform-specific dependency to `commonMain` by mistake; the build must reject it with a clear error.
- The Razorpay payment integration requires JavaScript SDK access; a JS interop bridge must be created in `wasmJsMain` without leaking into `commonMain`.
- A v2 breaking API change is introduced while v1 clients are still in use; `/api/v1/` and `/api/v2/` must co-exist temporarily until all clients are updated.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The backend MUST be a Kotlin + Spring Boot application located in a `backend/` folder at the repository root with its own `build.gradle.kts`.
- **FR-002**: The frontend MUST be a Compose Multiplatform project located in a `frontend/` folder at the repository root, entirely separate from the backend source tree.
- **FR-003**: The frontend MUST target Web (Wasm) as its initial production target; `commonMain` source set MUST contain all screen and navigation code.
- **FR-004**: The frontend project structure MUST accommodate Android and iOS as additional targets through source-set additions only, requiring no changes to existing `commonMain` code.
- **FR-005**: A shared KMP module MUST reside in a `shared/` folder at the repository root and MUST produce both a JVM artifact (for the backend) and a Wasm/JS artifact (for the frontend).
- **FR-006**: The shared module MUST contain at minimum: all domain DTOs exchanged over the REST API, kotlinx.serialization models, and shared validation functions.
- **FR-007**: The entire build system MUST use Gradle with Kotlin DSL (`build.gradle.kts`) throughout; no Groovy DSL is permitted.
- **FR-008**: The backend REST API MUST include a CORS configuration allowing the frontend origin in both development (`localhost`) and production environments.
- **FR-009**: The backend MUST use Spring Security with stateless JWT authentication; access tokens MUST be returned in the login response body and transmitted by the client via `Authorization: Bearer <token>` header; no server-side session state is stored; this applies uniformly to Wasm, Android, and iOS clients.
- **FR-010**: The frontend MUST use `ktor-client` with the `wasmJs` engine for all HTTP calls to the backend; no raw JavaScript fetch is used in `commonMain`.
- **FR-011**: The backend MUST use Spring Data JPA with Hibernate for database access; entities MUST map to the PostgreSQL schema defined in `specs/001-digital-reception-portal/data-model.md`.
- **FR-012**: The repository root MUST contain a multi-project Gradle settings file (`settings.gradle.kts`) that declares `backend`, `frontend`, and `shared` as subprojects.
- **FR-013**: The backend MUST be packageable as a Docker image via a `Dockerfile` at `backend/Dockerfile`; the image MUST start the Spring Boot application and accept `DATABASE_URL`, `JWT_SECRET`, and other secrets as environment variables.
- **FR-014**: All backend REST endpoints MUST be served under the `/api/v1/` path prefix; the Ktor client base URL in the shared module MUST be the single configuration point for this prefix, ensuring all KMP targets (Wasm, Android, iOS) automatically use the correct versioned path.

### Key Entities

- **Backend Module**: Spring Boot application responsible for REST API, authentication, database access, and business logic enforcement. Source root: `backend/src/main/kotlin/`.
- **Frontend Module**: Compose Multiplatform application targeting Web (Wasm) initially, with Android and iOS as planned future targets. Source root: `frontend/composeApp/src/`.
- **Shared Module**: Kotlin Multiplatform library with `commonMain`, `jvmMain`, and `wasmJsMain` source sets. Contains DTOs, validation, and serialization models. Source root: `shared/src/`.
- **Compose Screen**: A `@Composable` function in `commonMain` representing one user-facing view; must have no platform-specific imports.
- **JS Interop Bridge**: A `wasmJsMain`-only file wrapping browser or third-party JavaScript APIs (e.g., Razorpay, file picker) for consumption by `commonMain` via `expect/actual`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer following the quickstart guide can have all three modules building and the development servers running for backend and frontend within 30 minutes of cloning the repository.
- **SC-002**: Adding an Android or iOS target to the frontend project is achievable in under 2 hours of configuration work with no modifications to existing `commonMain` screen code.
- **SC-003**: All existing Digital Reception Portal functional requirements (FR-001 through FR-025 in spec 001) are fully deliverable within this architecture without adding new top-level modules or changing the folder structure.
- **SC-004**: Incremental builds (changing one Kotlin source file) complete in under 3 minutes for each module individually.
- **SC-005**: 100% of domain types exchanged between backend and frontend are defined in the shared module; zero separate/duplicated DTO definitions exist in `backend/` or `frontend/`.
- **SC-006**: The Compose Multiplatform web app renders all P1 resident workflows correctly in Chrome, Firefox, and Safari on both desktop and mobile-width viewports.

## Constitution Alignment Checklist *(mandatory)*

- Resident value is explicit: this architecture directly enables all resident-facing portal features defined in spec 001.
- Personal data flows only through typed, serialization-verified DTOs in the shared module; no untyped JSON is passed across the API boundary.
- Communication consent and preference fields are defined in shared DTOs used by both backend enforcement and frontend display.
- Reliability of notification delivery is supported by the backend's Spring Boot production-ready infrastructure and typed API contracts.
- Accessibility requirements are upheld in the Compose Multiplatform frontend through semantic composable structure and keyboard navigation support.

## Assumptions

- Gradle 8.x and JDK 21 (LTS) are the minimum build-tool versions.
- The PostgreSQL 15 database provisioned for spec 001 (Neon managed) is reused; no separate database instance is needed for this architecture.
- Compose Multiplatform for Web (Wasm) is accepted at Beta stability for this community portal; the trade-offs (bundle size, browser DevTools maturity) are acceptable given the behind-login, defined-user-base context.
- The frontend will be deployed as a static Wasm bundle served from Vercel (or equivalent CDN); no server-side rendering of the Compose frontend is required.
- The backend will be deployed as a Docker container on Railway or Render (PaaS); no manual server provisioning is required; secrets are injected as environment variables at runtime.
- All existing spec 001 API contract paths (`/api/auth/*`, `/api/notices/*`, etc.) are updated to `/api/v1/auth/*`, `/api/v1/notices/*`, etc. in the implementation; spec 001 contracts are the authoritative source of endpoint shape, not path prefix.
- Payment integration (Razorpay) will use JavaScript interop via a `wasmJsMain` bridge; this is a known Compose Multiplatform Web pattern and does not require a separate JS frontend.
- DLT-registered OTP delivery (MSG91 / Twilio) is invoked only from the Spring Boot backend; the frontend never calls SMS providers directly.
- The repository uses a single Gradle multi-project build at the root; backend, frontend, and shared are all subprojects of this root.
- JWT tokens are stored in-memory on the frontend (not in localStorage or cookies); the token is lost on page refresh and the user must re-authenticate. This is acceptable for the community portal use case and avoids XSS token theft.
