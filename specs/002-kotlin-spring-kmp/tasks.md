# Tasks: Platform Architecture — Kotlin/Spring Boot + Compose Multiplatform

**Input**: Design documents from `/specs/002-kotlin-spring-kmp/`  
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api-contracts.md](contracts/api-contracts.md), [contracts/build-contracts.md](contracts/build-contracts.md)  
**Generated**: 2026-05-14

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no in-progress dependencies)
- **[US#]**: Maps to user story — US1 (backend), US2 (frontend), US3 (shared module), US4 (mobile validation)
- **No story label**: Setup or Foundational phase — no user story

---

## Phase 1: Setup (Gradle Monorepo)

**Purpose**: Create the three-subproject Gradle build system before any source code is written. Nothing compiles until this phase is complete.

- [ ] T001 Create root Gradle project: settings.gradle.kts (declares :backend, :frontend, :shared), root build.gradle.kts (plugin declarations apply false), and Gradle wrapper (gradle/wrapper/gradle-wrapper.properties targeting Gradle 8.x) at repository root
- [ ] T002 [P] Create gradle/libs.versions.toml version catalog with all version pins from contracts/build-contracts.md (kotlin=2.1.0, compose-multiplatform=1.7.0, spring-boot=3.3.0, ktor=3.0.0, jjwt=0.12.6 and all libraries/plugins sections)
- [ ] T003 [P] Create backend/build.gradle.kts (kotlin("jvm"), spring-boot, spring-dependency-management plugins; spring-boot-starter-web, security, data-jpa, actuator, jjwt-api/impl/jackson, postgresql, jackson-module-kotlin, kotlin-reflect dependencies; implementation(project(":shared")) jvm target)
- [ ] T004 [P] Create shared/build.gradle.kts (kotlin-multiplatform + kotlin-serialization plugins; commonMain: kotlinx-serialization-json, kotlinx-coroutines-core; jvm() and wasmJs() targets; library publication config)
- [ ] T005 [P] Create frontend/build.gradle.kts (kotlin-multiplatform, compose-multiplatform, kotlin-compose plugins; wasmJs { browser { } } target; commonMain: compose.runtime, compose.foundation, compose.material3, ktor-client-core, ktor-client-content-negotiation, ktor-serialization-kotlinx-json, ktor-client-auth, compose-navigation, lifecycle-viewmodel-compose; wasmJsMain: ktor-client-js; implementation(project(":shared")) wasmJs target)
- [ ] T006 [P] Create backend/.env.example with all required env vars: DATABASE_URL, JWT_SECRET, JWT_EXPIRY_HOURS, CORS_ALLOWED_ORIGINS, MSG91_AUTH_KEY, MSG91_TEMPLATE_ID, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER, RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, CLOUDFLARE_R2_BUCKET, CLOUDFLARE_R2_ACCOUNT_ID, CLOUDFLARE_R2_ACCESS_KEY, CLOUDFLARE_R2_SECRET_KEY

**Checkpoint**: `./gradlew projects` must list `:backend`, `:frontend`, `:shared` with no configuration errors before proceeding.

---

## Phase 2: Foundational (Shared Module Skeleton)

**Purpose**: The `:shared` module must compile to JVM and Wasm *before* backend or frontend can depend on it. All domain types, auth DTOs, API constants, and validation functions needed by both US1 and US2 are defined here.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T007 Create directory tree shared/src/{commonMain,jvmMain,wasmJsMain}/kotlin/com/pph/shared/ and create shared/src/commonMain/kotlin/com/pph/shared/constants/ApiConstants.kt (object ApiConstants: BASE_URL, API_VERSION = "v1", BASE_PATH = "/api/v1", all sub-path constants, REQUEST_TIMEOUT_MS = 30_000L)
- [ ] T008 [P] Create shared/src/commonMain/kotlin/com/pph/shared/dto/auth/AuthDtos.kt: @Serializable data classes OtpRequestDto(mobileNumber: String), OtpVerifyDto(mobileNumber: String, otp: String), PasswordLoginDto(mobileNumber: String, password: String), AuthResponseDto(token: String, user: UserDto)
- [ ] T009 [P] Create shared/src/commonMain/kotlin/com/pph/shared/dto/UserDto.kt: @Serializable UserDto (id, fullName, mobileNumber, flatNumber, verificationStatus, roles, notificationPreferences, createdAt), VerificationStatus enum (UNVERIFIED, MOBILE_MATCHED, APPROVED, REJECTED), NotificationPreferences (smsEnabled, whatsappEnabled, consentGiven, consentTimestamp), RoleDto (id, name, permissions), ModulePermissions (canView, canCreate, canEdit, canDelete) — all @Serializable
- [ ] T010 [P] Create shared/src/commonMain/kotlin/com/pph/shared/dto/ApiError.kt: @Serializable data class ApiError(val code: String, val message: String, val details: Map<String, String> = emptyMap(), val status: Int = 400); sealed class ApiException(message: String) with code and status for common error codes
- [ ] T011 Create shared/src/commonMain/kotlin/com/pph/shared/validation/Validators.kt: object Validators with isValidMobile(mobile: String): Boolean (10-digit Indian mobile regex), isValidFlat(flat: String): Boolean (building-flat format regex), isValidOtp(otp: String): Boolean (6-digit numeric), sanitizeText(input: String): String (strips HTML tags)
- [ ] T012 Verify foundational build: run ./gradlew :shared:compileKotlinJvm :shared:compileKotlinWasmJs — both must succeed with zero errors before advancing to Phase 3

**Checkpoint**: Shared module compiles clean on both targets. Backend and frontend can now be scaffolded independently.

---

## Phase 3: User Story 1 — Operational Spring Boot Backend (Priority: P1) 🎯 MVP

**Goal**: A running Spring Boot REST API in backend/ that handles JWT auth, CORS, database connectivity, and serves at least the /api/v1/health, /api/v1/auth/*, and /api/v1/users/* endpoints.

**Independent Test**: `./gradlew :backend:bootRun` → `curl http://localhost:8080/api/v1/health` returns `{"status":"UP"}`. `curl http://localhost:8080/api/v1/users/me` returns 401 with ApiError body. `POST /api/v1/auth/otp/request` with valid mobile returns 200.

- [ ] T013 [US1] Create backend/src/main/kotlin/com/pph/backend/Application.kt: @SpringBootApplication class Application; fun main() calling runApplication<Application>(*args)
- [ ] T014 [P] [US1] Create backend/src/main/resources/application.properties: spring.datasource.url=${DATABASE_URL}, spring.jpa.hibernate.ddl-auto=update (dev), spring.jpa.show-sql=false, server.port=${PORT:8080}, management.endpoints.web.exposure.include=health, management.endpoint.health.show-details=always
- [ ] T015 [US1] Create backend/src/main/kotlin/com/pph/backend/config/JwtConfig.kt: @Configuration @ConfigurationProperties(prefix="jwt") data class JwtConfig(var secret: String = "", var expiryHours: Long = 1L); @EnableConfigurationProperties(JwtConfig::class) in Application.kt
- [ ] T016 [US1] Create backend/src/main/kotlin/com/pph/backend/auth/JwtService.kt: @Service using JJWT 0.12.x API — generateToken(userId: UUID, roles: List<String>): String (HS256, sub=userId, roles claim, exp=expiryHours), validateToken(token: String): Boolean, extractUserId(token: String): UUID, extractRoles(token: String): List<String>
- [ ] T017 [P] [US1] Create backend/src/main/kotlin/com/pph/backend/auth/JwtAuthenticationFilter.kt: OncePerRequestFilter — extract "Authorization: Bearer <token>", call JwtService.validateToken, set UsernamePasswordAuthenticationToken in SecurityContextHolder
- [ ] T018 [US1] Create backend/src/main/kotlin/com/pph/backend/config/SecurityConfig.kt: @Configuration @EnableWebSecurity — SecurityFilterChain bean: sessionCreationPolicy(STATELESS), addFilterBefore(JwtAuthenticationFilter), requestMatchers("/api/v1/auth/**", "/api/v1/health", "/api/v1/users/register").permitAll(), anyRequest().authenticated()
- [ ] T019 [P] [US1] Create backend/src/main/kotlin/com/pph/backend/config/CorsConfig.kt: @Configuration CorsConfigurationSource bean — allowedOrigins from CORS_ALLOWED_ORIGINS env var (split by comma), allowedMethods(GET, POST, PUT, PATCH, DELETE, OPTIONS), allowedHeaders(*), allowCredentials(false); register via CorsFilter bean
- [ ] T020 [US1] Create backend/src/main/kotlin/com/pph/backend/users/UserEntity.kt: @Entity @Table(name="users") data class UserEntity with @Id UUID id, fullName String, mobileNumber String (@Column unique), flatNumber String, verificationStatus VerificationStatus (@Enumerated STRING), passwordHash String?, createdAt Instant = Instant.now(); VerificationStatus enum mirrors shared module
- [ ] T021 [P] [US1] Create backend/src/main/kotlin/com/pph/backend/users/UserRepository.kt: interface UserRepository : JpaRepository<UserEntity, UUID> with findByMobileNumber(mobile: String): UserEntity?, existsByMobileNumber(mobile: String): Boolean
- [ ] T022 [US1] Create backend/src/main/kotlin/com/pph/backend/auth/AuthService.kt: @Service — OTP store as ConcurrentHashMap<String, Pair<String, Instant>>(mobile → otp+expiry); requestOtp(mobile): sends 6-digit OTP via MSG91 RestTemplate call (falls back to Twilio); verifyOtp(mobile, otp): validates store, issues JWT via JwtService; passwordLogin(mobile, password): BCryptPasswordEncoder.matches check
- [ ] T023 [US1] Create backend/src/main/kotlin/com/pph/backend/auth/AuthController.kt: @RestController @RequestMapping("/api/v1/auth") — POST /otp/request (OtpRequestDto → 200), POST /otp/verify (OtpVerifyDto → AuthResponseDto from :shared), POST /password/login (PasswordLoginDto → AuthResponseDto); uses shared module DTOs for request/response
- [ ] T024 [P] [US1] Create backend/src/main/kotlin/com/pph/backend/users/UserController.kt: @RestController @RequestMapping("/api/v1/users") — POST /register (RegisterRequestDto → UserDto 201), GET /me (→ UserDto 200), GET /{id} (→ UserDto 200, admin-only); response bodies use UserDto from :shared
- [ ] T025 [P] [US1] Create backend/src/main/kotlin/com/pph/backend/shared/ApiErrorHandler.kt: @RestControllerAdvice @ExceptionHandler(MethodArgumentNotValidException, AccessDeniedException, AuthenticationException, NoSuchElementException, Exception) mapping each to ApiError from :shared with appropriate HTTP status; ResponseEntity<ApiError>
- [ ] T026 [US1] Create backend/Dockerfile: multi-stage — Stage 1: FROM gradle:8-jdk21 AS builder, COPY . ., RUN ./gradlew :backend:bootJar --no-daemon; Stage 2: FROM eclipse-temurin:21-jre-alpine, COPY --from=builder backend/build/libs/*.jar app.jar, EXPOSE 8080, ENTRYPOINT ["java","-jar","/app.jar"]
- [ ] T027 [US1] Verify US1 acceptance criteria: start backend (./gradlew :backend:bootRun with DATABASE_URL set); assert (1) GET /api/v1/health → 200 {"status":"UP"}, (2) GET /api/v1/users/me without token → 401 ApiError, (3) POST /api/v1/auth/otp/request → 200, (4) CORS headers present on preflight from localhost:8081

---

## Phase 4: User Story 2 — Operational Compose Multiplatform Frontend (Priority: P1)

**Goal**: A running Compose Multiplatform web app in frontend/ that loads in a browser, renders the login screen, and can reach the backend to request and verify an OTP.

**Independent Test**: `./gradlew :frontend:wasmJsBrowserDevelopmentRun` → open http://localhost:8081 → LoginScreen renders with no console errors → submit mobile number → OTP request reaches backend and returns 200.

- [ ] T028 [US2] Create frontend/composeApp/src/wasmJsMain/kotlin/com/pph/frontend/main.kt: onWasmReady { ComposeViewport(document.body!!) { App() } }; add browser Wasm GC support check using window.WebAssembly?.compileStreaming availability before mounting
- [ ] T029 [US2] Create frontend/composeApp/src/commonMain/kotlin/com/pph/frontend/network/ApiClient.kt: Ktor HttpClient with engine from expect/actual or direct wasmJs engine; install ContentNegotiation(Json { ignoreUnknownKeys = true }); install Auth { bearer { loadTokens { BearerTokens(authStateFlow.value.token ?: "", "") } } }; baseUrl = ApiConstants.BASE_URL; suspend fun <T> get/post/patch helpers returning Result<T>
- [ ] T030 [P] [US2] Create frontend/composeApp/src/commonMain/kotlin/com/pph/frontend/theme/AppTheme.kt: @Composable AppTheme(content: @Composable () -> Unit) wrapping MaterialTheme with lightColorScheme (PPH brand primary color), Typography, and Shapes; export as the root theme wrapper
- [ ] T031 [US2] Create frontend/composeApp/src/commonMain/kotlin/com/pph/frontend/auth/AuthViewModel.kt: class AuthViewModel : ViewModel() with _authState: MutableStateFlow<AuthState> (Loading, Idle, OtpSent, Authenticated(user: UserDto, jwt: String), Error(msg)); fun requestOtp(mobile: String), fun verifyOtp(mobile: String, otp: String), fun logout(); JWT held in private _jwt: MutableStateFlow<String?> accessible from ApiClient
- [ ] T032 [US2] Create frontend/composeApp/src/commonMain/kotlin/com/pph/frontend/auth/LoginScreen.kt: @Composable LoginScreen(viewModel: AuthViewModel) — two-step form: step 1 = mobile number TextField + "Send OTP" Button (calls viewModel.requestOtp), step 2 = OTP TextField + "Verify" Button (calls viewModel.verifyOtp); shows CircularProgressIndicator during Loading state; keyboard accessibility (ImeAction.Done, focusManager.moveFocus)
- [ ] T033 [US2] Create frontend/composeApp/src/commonMain/kotlin/com/pph/frontend/navigation/NavGraph.kt: @Serializable route objects Login, Home; NavHost with startDestination = Login; collectAsState() from AuthViewModel.authState; LaunchedEffect to navigate Login→Home on Authenticated, Home→Login on logout; define placeholder HomeScreen composable
- [ ] T034 [US2] Create frontend/composeApp/src/commonMain/kotlin/com/pph/frontend/App.kt: @Composable App() creating AuthViewModel via viewModel(), wrapping AppTheme { NavGraph(authViewModel) }; check for Wasm feature support (expect/actual canRenderCompose(): Boolean) and show a Text("Your browser is not supported. Please upgrade to Chrome 112+, Firefox 120+, or Safari 17+.") fallback when false
- [ ] T035 [P] [US2] Create frontend/vercel.json: {"outputDirectory": "composeApp/build/dist/wasmJs/productionExecutable", "rewrites": [{"source": "/(.*)", "destination": "/index.html"}]}
- [ ] T036 [US2] Verify US2 acceptance criteria: run ./gradlew :frontend:wasmJsBrowserDevelopmentRun → confirm (1) app loads at http://localhost:8081 without console errors, (2) LoginScreen rendered with phone input, (3) OTP request submits to backend (observe network tab), (4) layout is usable at 375 px and 1280 px viewport widths, (5) all form fields reachable by Tab key

---

## Phase 5: User Story 3 — Complete Shared KMP Domain Module (Priority: P2)

**Goal**: All domain DTOs defined in data-model.md are present in shared/commonMain, both backend and frontend import them with zero duplicates (SC-005), and validators pass tests on both JVM and Wasm.

**Independent Test**: `./gradlew :shared:jvmTest` passes all Validators tests. Zero `data class` definitions in backend/ or frontend/ duplicate types from shared/commonMain.

- [ ] T037 [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/NoticeDto.kt: @Serializable NoticeDto (id, title, body, noticeType, authorId, authorName, publishAt, expiresAt?, attachmentUrl?, isPinned, createdAt), NoticeType enum (GENERAL, MAINTENANCE, EVENT, EMERGENCY, POLL), NoticeCreateDto
- [ ] T038 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/BookingDto.kt: @Serializable FacilitySlotDto (id, facilityName, date, startTime, endTime, status, bookedByUserId?, bookedByName?), SlotStatus enum (AVAILABLE, BOOKED, BLOCKED), BookingRequestDto (slotId, notes?), BookingDto (id, slot, resident, createdAt)
- [ ] T039 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/IssueDto.kt: @Serializable IssueTicketDto (id, title, description, category, status, reportedByUserId, assignedToUserId?, attachmentUrl?, createdAt, updatedAt), IssueCategory enum (PLUMBING, ELECTRICAL, SECURITY, COMMON_AREA, OTHER), IssueStatus enum (OPEN, IN_PROGRESS, RESOLVED, CLOSED), IssueCreateDto
- [ ] T040 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/FeedbackDto.kt: @Serializable FeedbackSubmissionDto (id, category, rating: Int, comment: String, isAnonymous: Boolean, submittedByUserId?, createdAt), FeedbackCreateDto
- [ ] T041 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/SummerCampDto.kt: @Serializable SummerCampRegistrationDto (id, childName, age, parentUserId, batchId, paymentStatus, createdAt), PaymentStatus enum (PENDING, PAID, FAILED, REFUNDED), PaymentOrderDto (orderId, amount, currency, key), PaymentWebhookDto (razorpayPaymentId, razorpayOrderId, razorpaySignature)
- [ ] T042 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/FormDto.kt: @Serializable DigitalFormRequestDto (id, formType, submittedByUserId, payload: Map<String, String>, status, createdAt), FormType enum (NOC, VENDOR_ENTRY, GATE_PASS, MOVE_IN, MOVE_OUT), FormStatus enum (PENDING, APPROVED, REJECTED)
- [ ] T043 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/AuditDto.kt: @Serializable AuditLogDto (id, action, actorUserId, actorName, targetEntityType, targetEntityId, details: Map<String, String>, createdAt), AuditAction enum (USER_APPROVED, USER_REJECTED, ROLE_ASSIGNED, NOTICE_CREATED, BOOKING_CREATED, BOOKING_CANCELLED, ISSUE_STATUS_CHANGED, FORM_APPROVED, FORM_REJECTED, PAYMENT_RECEIVED, CONTENT_UPDATED, AUDIT_EXPORTED)
- [ ] T044 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/PagedResponse.kt: @Serializable data class PagedResponse<T>(val items: List<T>, val totalCount: Int, val page: Int, val pageSize: Int, val totalPages: Int)
- [ ] T045 [P] [US3] Create shared/src/commonMain/kotlin/com/pph/shared/dto/UiState.kt: sealed class UiState<out T> { object Loading : UiState<Nothing>(); data class Success<T>(val data: T) : UiState<T>(); data class Error(val message: String, val code: String = "") : UiState<Nothing>() }
- [ ] T046 [US3] Create backend/src/main/kotlin/com/pph/backend/shared/Mappers.kt: extension functions UserEntity.toDto(): UserDto, NoticeEntity.toDto(): NoticeDto (import DTOs from :shared; no JPA annotations in shared module); add NoticeEntity.kt JPA entity to backend/src/main/kotlin/com/pph/backend/notices/NoticeEntity.kt
- [ ] T047 [US3] Write shared/src/jvmTest/kotlin/com/pph/shared/ValidatorsTest.kt: JUnit 5 @Test functions asserting isValidMobile("9876543210") == true, isValidMobile("123") == false, isValidFlat("A-101") == true, isValidFlat("") == false, isValidOtp("123456") == true, isValidOtp("12345") == false, sanitizeText("<script>alert(1)</script>") does not contain "<script>"; run ./gradlew :shared:jvmTest and confirm all pass
- [ ] T048 [US3] Verify SC-005 compliance: search backend/src/main/kotlin and frontend/composeApp/src for standalone data class definitions; confirm zero types duplicate DTOs already defined in shared/src/commonMain; document verification result in plan.md Complexity Tracking table

---

## Phase 6: User Story 4 — Mobile-Ready Architecture Validated (Priority: P2)

**Goal**: Confirm that adding an Android target to the frontend project requires only build-file changes and creates zero modifications to existing commonMain screen code.

**Independent Test**: `./gradlew :frontend:compileKotlinAndroid` succeeds with the androidTarget() added. All existing @Composable screens in commonMain compile for Android without changes.

- [ ] T049 [US4] Add androidTarget() to frontend/composeApp/build.gradle.kts kotlin {} block; add android { compileSdk = 34; defaultConfig { minSdk = 26; targetSdk = 34 } } block; add android-specific dependencies (androidx.activity:activity-compose) in androidMain source set dependencies
- [ ] T050 [US4] Create stub frontend/composeApp/src/androidMain/kotlin/com/pph/frontend/MainActivity.kt: class MainActivity : ComponentActivity() { override fun onCreate() { super.onCreate(savedInstanceState); setContent { App() } } } — no commonMain imports modified
- [ ] T051 [US4] Run ./gradlew :frontend:compileKotlinAndroid and verify all commonMain sources (App.kt, LoginScreen.kt, NavGraph.kt, AuthViewModel.kt, ApiClient.kt) compile for Android target with zero errors and zero modifications
- [ ] T052 [US4] Remove androidTarget() block and android {} block from frontend/composeApp/build.gradle.kts; delete frontend/composeApp/src/androidMain/kotlin/com/pph/frontend/MainActivity.kt (architecture validated; Android not in active scope — reactivation documented in quickstart.md "Adding Mobile Targets" section)
- [ ] T053 [US4] Confirm iOS-readiness: verify no file in frontend/composeApp/src/commonMain imports android.* packages or uses Android-only Modifier extensions (grep commonMain for "android\." imports); document finding in specs/002-kotlin-spring-kmp/spec.md Assumptions section

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Spec 001 plan alignment, security hardening, observability, deployment validation, and success criteria verification.

- [ ] T054 [P] Update specs/001-digital-reception-portal/plan.md Technical Context section: replace "TypeScript 5.x / Node.js 20 LTS + Next.js 14" with "Kotlin 2.x / JVM 21 — Spring Boot backend; Kotlin/Wasm — Compose Multiplatform frontend; superseded by spec 002 (see specs/002-kotlin-spring-kmp/plan.md)"; add a note that tasks.md implementation follows spec 002 architecture
- [ ] T055 [P] Update .gitignore from current 3-line version to include Kotlin/Gradle project exclusions: build/, .gradle/, *.jar, .env, .env.*, *.class, out/, dist/, *.iml, .idea/, *.kotlin_module, local.properties
- [ ] T056 [P] Create frontend/composeApp/src/wasmJsMain/kotlin/com/pph/frontend/interop/RazorpayBridge.kt: stub expect/actual pattern — expect fun openRazorpayCheckout(options: Map<String, String>, onSuccess: (String) -> Unit, onFailure: (String) -> Unit); actual in wasmJsMain using @JsModule("razorpay") JS interop; commonMain calls expect function without platform awareness
- [ ] T057 [P] Add OTP rate-limiting guard to backend/src/main/kotlin/com/pph/backend/auth/AuthService.kt: ConcurrentHashMap<String, Instant> tracking last OTP request time per mobile; reject with ApiError(code="OTP_RATE_LIMIT_EXCEEDED", status=429) if same mobile requests again within 60 seconds
- [ ] T058 Verify SC-001: follow quickstart.md step-by-step from a clean state (no .env, no build cache) — measure time from `git clone` to all three modules building and both development servers running; confirm ≤ 30 minutes; update quickstart.md if any step fails or is inaccurate
- [ ] T059 [P] Verify SC-004 incremental build: touch backend/src/main/kotlin/com/pph/backend/auth/AuthService.kt (add a blank line), run ./gradlew :backend:compileKotlin, confirm completion in ≤ 3 minutes; repeat for :shared and :frontend
- [ ] T060 [P] Verify SC-006 cross-browser rendering: run ./gradlew :frontend:wasmJsBrowserDistribution, open productionExecutable/index.html in Chrome, Firefox, and Safari; confirm LoginScreen renders without errors at 1280 px and 375 px viewport widths; confirm keyboard navigation reaches all interactive elements

---

## Dependency Graph

```
Phase 1 (Setup)
    │
    ▼
Phase 2 (Foundational — Shared skeleton)
    │
    ├───────────────────────────┐
    ▼                           ▼
Phase 3 (US1 — Backend)     Phase 4 (US2 — Frontend)
    │                           │
    └─────────┬─────────────────┘
              ▼
         Phase 5 (US3 — Shared complete)
              │
         Phase 6 (US4 — Mobile validation)  ← can run in parallel with Phase 5
              │
         Phase 7 (Polish)
```

**Story dependency order**:
- Shared module skeleton (Phase 2) MUST complete before any story work begins
- US1 (Phase 3) and US2 (Phase 4) are independent and can run in **parallel** after Phase 2
- US3 (Phase 5) should start after Phase 3 and Phase 4 are substantially complete (backend mappers depend on US1 entities)
- US4 (Phase 6) requires Phase 4 complete (needs commonMain screens)
- Polish (Phase 7) runs last

---

## Parallel Execution Examples

### After Phase 2 completes — run in parallel

**Workstream A (US1 — Backend)**:
```
T013 → T015 → T016 → T017 → T018 → T020 → T022 → T023 → T027
T014, T019, T021, T024, T025 [P] alongside above
```

**Workstream B (US2 — Frontend)**:
```
T028 → T031 → T032 → T033 → T036
T029, T030, T035 [P] alongside above
```

### After Phase 5 starts

**Workstream C (US3 DTOs)**:
```
T037 → T046 → T047 → T048
T038–T045 [P] alongside T037
```

**Workstream D (US4 mobile validation — can overlap with US3)**:
```
T049 → T050 → T051 → T052 → T053
```

---

## Implementation Strategy

**MVP** (deliver in order): Phase 1 → Phase 2 → Phase 3 (US1 only)
- Result: Running Spring Boot backend with JWT auth, CORS, health endpoint, and PostgreSQL connectivity
- Can be tested from any REST client (Postman, curl) before the frontend exists
- Unblocks all backend feature development for spec 001

**MVP+** (full working stack): + Phase 4 (US2)
- Result: Compose Multiplatform frontend serving the login flow end-to-end against the live backend
- Unblocks all resident-facing feature UI development

**Full Architecture Validation**: + Phase 5 + Phase 6
- Result: All domain DTOs shared, zero duplication confirmed, mobile readiness proven

---

## Task Count Summary

| Phase | Tasks | User Story | Parallelizable |
|---|---|---|---|
| Phase 1: Setup | 6 | — | T002–T006 [P] |
| Phase 2: Foundational | 6 | — | T008–T010 [P] |
| Phase 3: US1 Backend | 15 | US1 (P1) | T014, T017, T019, T021, T024, T025 [P] |
| Phase 4: US2 Frontend | 9 | US2 (P1) | T030, T035 [P] |
| Phase 5: US3 Shared | 12 | US3 (P2) | T038–T045 [P] |
| Phase 6: US4 Mobile | 5 | US4 (P2) | — |
| Phase 7: Polish | 7 | — | T054–T057, T059, T060 [P] |
| **Total** | **60** | | **25 parallelizable** |

**MVP scope** (Phase 1 + 2 + 3): 27 tasks
