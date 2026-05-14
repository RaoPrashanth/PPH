---
description: "Task list for Digital Reception Portal implementation"
---

# Tasks: Digital Reception Portal

**Input**: `specs/001-digital-reception-portal/` — plan.md, spec.md, data-model.md, contracts/api-contracts.md, research.md, quickstart.md  
**Branch**: `master` | **Date**: 2026-05-14

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[US1]–[US5]**: User story this task belongs to (user story phases only)
- No [P] / [Story] labels in Setup or Foundational phases

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize the Next.js 14 TypeScript monolith and shared tooling.

- [ ] T001 Initialize Next.js 14 TypeScript project with pnpm, App Router, and `src/` directory at repository root
- [ ] T002 [P] Configure ESLint (eslint-config-next), Prettier, and tsconfig.json at repository root
- [ ] T003 [P] Configure Tailwind CSS with responsive breakpoints (320 px / 768 px / 1280 px) and design tokens in tailwind.config.ts
- [ ] T004 [P] Create .env.example with all required environment variable keys per quickstart.md (DATABASE_URL, NEXTAUTH_SECRET, MSG91_AUTH_KEY, TWILIO_*, RAZORPAY_*, R2_*)

**Checkpoint**: Project scaffolded, tooling configured — foundational implementation can now begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before any user story begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T005 Define complete Prisma schema with all 9 entities (User, Role, FacilitySlot, SummerCampRegistration, DigitalFormRequest, NoticeItem, FeedbackSubmission, IssueTicket, AuditLog) and all enums in prisma/schema.prisma
- [ ] T006 Generate and apply initial Prisma database migration in prisma/migrations/
- [ ] T007 Create Prisma seed script with 3 Role rows (resident, elected_body, admin with default permission JSONB) and one admin User (flat MGMT-01) in prisma/seed.ts
- [ ] T008 [P] Implement Prisma client singleton with connection pool configuration in src/lib/db.ts
- [ ] T009 [P] Implement OTP dispatch helper with MSG91 primary and Twilio fallback, including 3-per-10-min rate limiting in src/lib/otp.ts
- [ ] T010 Configure NextAuth.js v5 with OTPCredentialsProvider (mobile + OTP) and PasswordCredentialsProvider (mobile + password), embedding role and verification_status in the JWT session in src/lib/auth.ts
- [ ] T011 Implement RBAC permission check helpers that look up Role.permissions JSONB and assert required flag for a given module action in src/lib/permissions.ts
- [ ] T012 [P] Implement AuditLog write helper covering all 12 action constants (USER_REGISTERED, USER_APPROVED, USER_REJECTED, USER_ROLE_CHANGED, PERMISSION_UPDATED, NOTICE_PUBLISHED, NOTICE_RETRACTED, BOOKING_CREATED, BOOKING_CANCELLED, FORM_REVIEWED, ISSUE_STATUS_CHANGED, CONTENT_UPDATED) in src/lib/audit.ts
- [ ] T013 [P] Implement OTP request POST and OTP verify POST API routes in src/app/api/auth/otp/
- [ ] T014 [P] Implement password login POST API route in src/app/api/auth/password/login/route.ts
- [ ] T015 [P] Create base UI component library (Button, Input, Card, Badge, Spinner, Modal) in src/components/ui/
- [ ] T016 [P] Create layout components (Header, Sidebar, NavLink, PageWrapper) with responsive mobile and desktop variants in src/components/layout/
- [ ] T017 Create authentication pages (OTP login form, registration form with unit+mobile fields, pending-approval status screen) in src/app/(auth)/
- [ ] T018 Implement user registration POST API route with unit-number + mobile verification against resident records (FR-022, FR-023) in src/app/api/users/register/route.ts
- [ ] T019 [P] Implement user profile GET and PATCH API route including communication_consent management (FR-016) in src/app/api/users/me/route.ts
- [ ] T020 [P] Configure axe-core accessibility CI check (per constitution Accessibility Gate) in CI configuration at repository root
- [ ] T021 Implement NextAuth.js session middleware enforcing approved-session guard on (resident), admin role on (admin), and elected_body role on (elected-body) route groups in src/middleware.ts

**Checkpoint**: Foundation complete — all user story phases can now begin (in priority order or in parallel if staffed).

---

## Phase 3: User Story 1 — Access Community Information Instantly (Priority: P1) 🎯 MVP

**Goal**: Residents can browse facility timings, SOPs, emergency contacts, and search FAQs without contacting the management office. Authorized admins can update all content.

**Independent Test**: Open the portal as an approved resident; navigate to Facility Timings, SOPs, Emergency Contacts, and FAQ; confirm all required details are visible and the FAQ search returns matching results. Confirm unauthenticated access is blocked.

- [ ] T022 [P] [US1] Implement facility timings GET (public read) and PATCH (content_management.update) API routes in src/app/api/content/facility-timings/route.ts
- [ ] T023 [P] [US1] Implement SOPs list GET and SOP document PATCH API routes in src/app/api/content/sops/route.ts
- [ ] T024 [P] [US1] Implement emergency contacts GET and PATCH API routes in src/app/api/content/emergency-contacts/route.ts
- [ ] T025 [P] [US1] Implement FAQ search GET API route with `?q=` keyword filtering in src/app/api/content/faq/route.ts
- [ ] T026 [US1] Create Digital Reception home page with clearly discoverable section navigation for Information Desk, Concierge, Notice Board, and Feedback Counter (FR-001) in src/app/(resident)/page.tsx
- [ ] T027 [P] [US1] Create Facility Timings page showing current operating hours for gym, pool, and sports areas in src/app/(resident)/information/timings/page.tsx
- [ ] T028 [P] [US1] Create SOPs page displaying procedures for party hall booking, move-in/out, and visitor parking in src/app/(resident)/information/sops/page.tsx
- [ ] T029 [P] [US1] Create Emergency Contacts page with one-tap `tel:` links for security gate, estate manager, plumber, and electrician in src/app/(resident)/information/emergency-contacts/page.tsx
- [ ] T030 [P] [US1] Create FAQ page with keyword search input and filtered answer display (FR-005) in src/app/(resident)/information/faq/page.tsx
- [ ] T031 [P] [US1] Create admin content management pages for editing facility timings, SOPs, and emergency contacts in src/app/(admin)/content/
- [ ] T032 [US1] Add CONTENT_UPDATED AuditLog entry to all content PATCH handlers and add server-side observability logging in src/app/api/content/
- [ ] T033 [US1] Add accessibility acceptance checks for all Information Desk pages: keyboard navigation, color contrast, semantic headings, and mobile viewport in src/app/(resident)/information/
- [ ] T034 [US1] Verify all Information Desk pages return 401 for unauthenticated requests and 403 for non-approved users via src/middleware.ts

**Checkpoint**: User Story 1 fully functional — residents can self-serve all community information independently.

---

## Phase 4: User Story 2 — Complete Core Requests Digitally (Priority: P1)

**Goal**: Residents can book amenity slots with conflict prevention, register children for summer camp with payment, download and submit society forms, and access all four quick actions from the home page.

**Independent Test**: Book a gym slot as resident A; attempt simultaneous booking as resident B and confirm 409 conflict. Register a child for summer camp, initiate Razorpay order, simulate payment.captured webhook, and confirm schedule unlocks. Download an interiors work permission form. Submit a vehicle sticker request. Verify quick action shortcuts on home page navigate to correct flows.

- [ ] T035 [P] [US2] Implement R2/S3 object storage helper with signed download URL generation for form PDF templates in src/lib/storage.ts
- [ ] T036 [P] [US2] Implement amenity booking service with PostgreSQL SELECT FOR UPDATE conflict prevention and ULID confirmation code generation in src/services/booking-service.ts
- [ ] T037 [P] [US2] Implement payment service with Razorpay order creation, idempotency key management, and payment.captured event handling in src/services/payment-service.ts
- [ ] T038 [P] [US2] Implement facility slot listing GET (with `?facility=` and `?date=` params) and reservation POST API routes in src/app/api/bookings/route.ts
- [ ] T039 [P] [US2] Implement booking cancellation DELETE API route (resident cancels own; admin requires bookings.manage_all) in src/app/api/bookings/[slotId]/route.ts
- [ ] T040 [P] [US2] Implement summer camp registration POST API route in src/app/api/summer-camp/register/route.ts
- [ ] T041 [P] [US2] Implement Razorpay payment order creation POST API route in src/app/api/summer-camp/payment/order/route.ts
- [ ] T042 [US2] Implement Razorpay webhook POST handler with HMAC signature verification, setting payment_status=paid and schedule_access_unlocked=true on payment.captured in src/app/api/summer-camp/payment/webhook/route.ts
- [ ] T043 [P] [US2] Implement summer camp schedule GET API route gated on schedule_access_unlocked=true in src/app/api/summer-camp/schedule/route.ts
- [ ] T044 [P] [US2] Implement digital form templates list GET (with signed R2 download URLs) and form submission POST API routes in src/app/api/forms/route.ts
- [ ] T045 [P] [US2] Implement admin digital form review PATCH API route (approve/reject with review notes, requires forms.manage) in src/app/api/forms/[id]/review/route.ts
- [ ] T046 [P] [US2] Create slot picker component with facility selector, date picker, and available slot grid in src/components/bookings/SlotPicker.tsx
- [ ] T047 [P] [US2] Create booking confirmation card component displaying confirmation code, facility, and time in src/components/bookings/ConfirmationCard.tsx
- [ ] T048 [US2] Create amenity booking page composing SlotPicker and ConfirmationCard with full reservation flow in src/app/(resident)/concierge/booking/page.tsx
- [ ] T049 [US2] Create summer camp page with child registration form, Razorpay payment initiation, and schedule download section in src/app/(resident)/concierge/summer-camp/page.tsx
- [ ] T050 [US2] Create digital forms hub page with downloadable template list and form submission flow per form_type in src/app/(resident)/concierge/forms/page.tsx
- [ ] T051 [US2] Add Pay CAM, Book Court, Summer Camp, and Register Complaint quick action entry points to Digital Reception home page in src/app/(resident)/page.tsx
- [ ] T052 [P] [US2] Create admin digital form review page listing submitted requests with approve/reject actions in src/app/(admin)/forms/page.tsx
- [ ] T053 [US2] Add accessibility acceptance checks for booking slot picker, summer camp flow, and forms submission pages in src/app/(resident)/concierge/
- [ ] T054 [US2] Add reliability logging (conflict check result, BOOKING_CREATED audit, webhook receipt timestamp) to booking service and payment service in src/services/booking-service.ts and src/services/payment-service.ts

**Checkpoint**: User Story 2 fully functional — residents can complete all concierge workflows digitally without offline channels.

---

## Phase 5: User Story 5 — Manage Roles and Privileged Permissions (Priority: P1)

**Goal**: Admins can approve/reject pending resident accounts, assign roles, and toggle module-level permission flags per role. Elected body members can manage bulletins and advance issue lifecycle. All privileged actions write AuditLog entries. RBAC enforcement blocks unauthorized access at the route level.

**Independent Test**: Register a new resident and confirm pending-approval status. Log in as admin; approve account; change role to elected_body; verify new role session immediately. Toggle a permission flag and verify the change is reflected in subsequent API calls. Log in as resident; confirm admin and elected-body routes return 403. View audit log and confirm all actions are recorded with actor, timestamp, and target.

- [ ] T055 [P] [US5] Implement user management API routes: list users GET (paginated, filterable by status), approve POST, reject POST, change role PATCH in src/app/api/users/
- [ ] T056 [P] [US5] Implement permissions API routes: list roles with permissions GET and update module flags PATCH in src/app/api/permissions/
- [ ] T057 [P] [US5] Implement audit log API route with actor_id, action, date-range filtering and pagination in src/app/api/audit/route.ts
- [ ] T058 [US5] Implement audit log archival Vercel cron job: export AuditLog rows older than 12 months to R2 as JSONL and soft-delete from live table in src/app/api/cron/archive-audit/route.ts
- [ ] T059 [P] [US5] Create admin user approval queue page listing mobile_matched users with approve and reject actions in src/app/(admin)/users/page.tsx
- [ ] T060 [P] [US5] Create admin role management page listing all users with role badges and role reassignment control in src/app/(admin)/users/roles/page.tsx
- [ ] T061 [P] [US5] Create admin permission module toggles page with per-role boolean flag editing for all 6 modules in src/app/(admin)/permissions/page.tsx
- [ ] T062 [P] [US5] Create admin audit log viewer page with actor, action, date-range filters and paginated results in src/app/(admin)/audit/page.tsx
- [ ] T063 [US5] Create elected body bulletins management page for creating and listing EC bulletins (requires notices.publish) in src/app/(elected-body)/bulletins/page.tsx
- [ ] T064 [US5] Create elected body issues management page for acknowledging and advancing issue lifecycle (requires issue_routing.manage) in src/app/(elected-body)/issues/page.tsx
- [ ] T065 [US5] Verify resident session cannot access any (admin) or (elected-body) route; confirm middleware returns 403 for role mismatch in src/middleware.ts
- [ ] T066 [US5] Verify elected-body session cannot access user_management or permissions routes reserved for admin in src/middleware.ts
- [ ] T067 [US5] Add accessibility acceptance checks for admin approval queue, role management, and permissions toggle pages in src/app/(admin)/
- [ ] T068 [US5] Validate all 12 AuditLog action types correctly write actor_id, target_type, target_id, previous_state, new_state, and performed_at in src/lib/audit.ts
- [ ] T069 [P] [US5] Implement archived audit log download API route returning signed R2 URL for JSONL export (requires admin) in src/app/api/audit/archive/[year]/[month]/route.ts

**Checkpoint**: User Story 5 fully functional — RBAC enforced at all route groups; admin can manage all users, roles, and permissions; full privileged-action audit trail operational.

---

## Phase 6: User Story 3 — Stay Updated with Real-Time Community Notices (Priority: P2)

**Goal**: Residents see a live urgent status banner, EC bulletins in chronological order, and a days-to-go event countdown — all updated in real-time via SSE with a polling fallback. Authorized publishers can create and retract notices.

**Independent Test**: Publish an urgent banner as elected body member; confirm resident portal displays the banner within 60 seconds (SSE path and polling fallback path independently). Publish an EC bulletin and an event countdown; verify both display correctly. Set expires_at in the past; confirm the notice becomes inactive.

- [ ] T070 [P] [US3] Implement notice service (create notice, trigger SSE fanout, expire notice, retract notice) in src/services/notice-service.ts
- [ ] T071 [P] [US3] Implement SSE connection manager with active-client registry and per-event fanout in src/lib/sse.ts
- [ ] T072 [P] [US3] Implement active notices GET (filterable by type) and notice creation POST API routes in src/app/api/notices/route.ts
- [ ] T073 [P] [US3] Implement notice retraction DELETE API route (requires notices.delete) with NOTICE_RETRACTED audit entry in src/app/api/notices/[id]/route.ts
- [ ] T074 [US3] Implement SSE notices endpoint streaming notice_published and notice_retracted events to connected clients in src/app/api/sse/notices/route.ts
- [ ] T075 [US3] Implement notice expiry Vercel cron job setting is_active=false when expires_at is reached and triggering SSE fanout in src/app/api/cron/expire-notices/route.ts
- [ ] T076 [P] [US3] Create live banner component with SSE connection hook and automatic 30-second polling fallback for proxy-blocked environments in src/components/notices/LiveBanner.tsx
- [ ] T077 [P] [US3] Create EC bulletin card component with chronological list layout in src/components/notices/BulletinCard.tsx
- [ ] T078 [P] [US3] Create event countdown component computing and displaying days-to-go from event_date in src/components/notices/EventCountdown.tsx
- [ ] T079 [US3] Create Notice Board page composing LiveBanner, EC bulletins list, and EventCountdown in src/app/(resident)/notices/page.tsx
- [ ] T080 [P] [US3] Create admin/elected-body notice publishing form page with notice_type selector, title, body, publish_at, and expires_at fields in src/app/(admin)/notices/page.tsx
- [ ] T081 [US3] Add reliability verification confirming SSE fanout reaches connected clients and polling fallback satisfies SC-004 (95% of notices visible within 60 s) in src/services/notice-service.ts
- [ ] T082 [US3] Add accessibility acceptance checks for Notice Board page (live banner keyboard accessibility, bulletin list semantic structure, countdown readable text) in src/app/(resident)/notices/

**Checkpoint**: User Story 3 fully functional — residents receive live community updates; authorized publishers can manage all notice types.

---

## Phase 7: User Story 4 — Submit Suggestions and Report Issues (Priority: P2)

**Goal**: Residents can submit suggestions and file maintenance issue reports with automatic team routing and tracking references. Issue lifecycle (New → Acknowledged → In Progress → Resolved → Closed) is enforced and visible to residents.

**Independent Test**: Submit a suggestion; confirm acknowledgement_status=received. File a plumbing issue; confirm lifecycle_status=new, assigned_team=Plumbing Team, and a ticket ID is returned. As elected body member, advance status to Acknowledged; confirm resident sees updated status. Attempt backward transition; confirm 400 rejection.

- [ ] T083 [P] [US4] Implement issue service with forward-only lifecycle transition validation and category-to-team routing map in src/services/issue-service.ts
- [ ] T084 [P] [US4] Implement feedback submission POST API route returning acknowledgement_status=received in src/app/api/feedback/route.ts
- [ ] T085 [P] [US4] Implement issue creation POST and ops-level GET API routes (POST for residents; GET with status/category filters for ops/admin) in src/app/api/issues/route.ts
- [ ] T086 [P] [US4] Implement resident own-issues GET API route returning paginated IssueTicket list for the authenticated user in src/app/api/issues/mine/route.ts
- [ ] T087 [US4] Implement issue lifecycle status transition PATCH API route enforcing forward-only validation and writing ISSUE_STATUS_CHANGED audit entry in src/app/api/issues/[id]/status/route.ts
- [ ] T088 [P] [US4] Create suggestion submission form component with category selector and message textarea in src/components/feedback/SuggestionForm.tsx
- [ ] T089 [P] [US4] Create issue report form component with category selector, severity selector, location description, and description fields in src/components/feedback/IssueForm.tsx
- [ ] T090 [P] [US4] Create issue status badge component displaying current lifecycle_status with appropriate color coding in src/components/feedback/StatusBadge.tsx
- [ ] T091 [US4] Create Feedback Counter page composing SuggestionForm and IssueForm with submission confirmation messages (FR-018) in src/app/(resident)/feedback/page.tsx
- [ ] T092 [P] [US4] Create resident issue tracking view listing own tickets with StatusBadge and assigned_team display in src/app/(resident)/feedback/issues/page.tsx
- [ ] T093 [US4] Add accessibility acceptance checks for suggestion form, issue report form, and issue tracking view in src/app/(resident)/feedback/
- [ ] T094 [US4] Add reliability logging (routing decision, lifecycle transition, ISSUE_STATUS_CHANGED audit entry) to issue service in src/services/issue-service.ts
- [ ] T095 [US4] Validate issue lifecycle service rejects backward transitions (e.g., in_progress → new) with a 400 error and does not modify state in src/services/issue-service.ts

**Checkpoint**: User Story 4 fully functional — resident feedback and issue reporting channel is fully operational with lifecycle tracking.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, cross-story quality gates, and production readiness.

- [ ] T096 [P] Run quickstart.md end-to-end validation: pnpm install → prisma db push → prisma db seed → pnpm dev → register → OTP login → portal home loads correctly
- [ ] T097 [P] Validate communication consent behavior: confirm non-emergency notices are suppressed for users with communication_consent=false and emergency notices reach all users regardless of consent flag
- [ ] T098 Validate accessibility acceptance criteria across all resident-facing screens (all (resident) route group pages) using axe-core CLI — confirm zero critical violations
- [ ] T099 [P] Validate observability completeness: verify all 12 AuditLog action types produce entries with actor_id and performed_at; verify server logs capture auth.attempt, auth.success, auth.failure, SSE fanout count, and booking conflict check result
- [ ] T100 [P] Performance review: measure P95 page load time (target < 2 s), booking confirmation time (target < 3 s per SC-002), and live notice delivery time (target < 60 s per SC-004) under representative load
- [ ] T101 [P] Security review: verify Razorpay HMAC webhook validation (T042), RBAC middleware coverage across all route groups (T021, T065, T066), OTP rate limiting enforcement (T013), and absence of PII (mobile, flat_number) in application log output
- [ ] T102 [P] Write README.md at repository root with project overview, setup instructions linking to quickstart.md, and references to plan.md and spec.md
- [ ] T103 Final integration smoke test: complete one full resident journey — register → unit+mobile verify → admin approve → OTP login → book amenity → submit issue → view notice board — per quickstart.md scenarios

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — **BLOCKS all user story phases**
- **User Story Phases (3–7)**: All depend on Phase 2 completion; can proceed in priority order or in parallel if team capacity allows
- **Polish (Phase N)**: Depends on all desired user story phases being complete

### User Story Dependencies

| Phase | Story | Priority | Depends On | Cross-Story Integration |
|---|---|---|---|---|
| Phase 3 | US1 — Information Desk | P1 | Phase 2 | None — fully independent |
| Phase 4 | US2 — Concierge | P1 | Phase 2 | Adds quick actions to US1 home page (T051) |
| Phase 5 | US5 — RBAC / Admin | P1 | Phase 2 | Enforces permissions used by all stories |
| Phase 6 | US3 — Notice Board | P2 | Phase 2 | None — fully independent |
| Phase 7 | US4 — Feedback | P2 | Phase 2 | Issue mgmt page complements US5 elected-body panel |

### Within Each User Story

- API/service tasks first, then page/component tasks
- Parallelizable tasks ([P]) can run simultaneously within their story phase
- Accessibility, logging, and verification tasks come last within each story
- Story is shippable after its checkpoint; next story can begin in parallel

### Parallel Execution Examples

**Phase 2 parallel opportunities** (after T005–T007 complete):
```
T008 (Prisma client)     ┐
T009 (OTP lib)           ├── run in parallel
T012 (audit helper)      ┤
T015 (UI components)     ┘
```

**Phase 3 parallel opportunities** (after T021 — session middleware):
```
T022 (timings API)       ┐
T023 (SOPs API)          ├── run in parallel
T024 (contacts API)      ┤
T025 (FAQ API)           ┘
T027 (timings page)      ┐
T028 (SOPs page)         ├── run in parallel
T029 (contacts page)     ┤
T030 (FAQ page)          ┘
```

**Phase 4 parallel opportunities** (after T005 schema):
```
T035 (R2 storage)        ┐
T036 (booking service)   ├── run in parallel
T037 (payment service)   ┤
T046 (slot picker)       ┘
T047 (confirmation card) ┘
```

**Phase 5 parallel opportunities**:
```
T055 (user mgmt APIs)    ┐
T056 (permissions APIs)  ├── run in parallel
T057 (audit API)         ┤
T059 (approval queue)    ┘
T060 (role management)   ┐
T061 (permissions UI)    ├── run in parallel
T062 (audit viewer)      ┘
```

---

## Implementation Strategy

### MVP Scope (deliver US1 only — Phase 1 + Phase 2 + Phase 3)

Completing Phases 1–3 (T001–T034) delivers a fully usable Digital Reception
information portal: residents can register, log in (OTP), be approved by admin,
and access all community information (facility timings, SOPs, emergency contacts,
FAQ search). This is independently demonstrable and covers SC-001.

**MVP task count**: 34 tasks

### Incremental Delivery After MVP

| Increment | Phases | Adds |
|---|---|---|
| MVP + Concierge | + Phase 4 | Amenity booking, summer camp, digital forms, quick actions (SC-002, SC-003) |
| + Governance | + Phase 5 | Full RBAC admin UI, permission toggles, audit viewer (SC-007, SC-009) |
| + Notices | + Phase 6 | Real-time notice board, SSE live updates (SC-004) |
| + Feedback | + Phase 7 | Suggestions, issue lifecycle, team routing (SC-005) |
| Full release | + Phase N | Validation, accessibility, security, performance review |

---

## Task Summary

| Phase | Story | Tasks | Parallel Opportunities |
|---|---|---|---|
| Phase 1 — Setup | — | T001–T004 (4) | 3 of 4 parallelizable |
| Phase 2 — Foundational | — | T005–T021 (17) | 10 of 17 parallelizable |
| Phase 3 — US1 Information Desk | P1 | T022–T034 (13) | 9 of 13 parallelizable |
| Phase 4 — US2 Concierge | P1 | T035–T054 (20) | 13 of 20 parallelizable |
| Phase 5 — US5 RBAC / Admin | P1 | T055–T069 (15) | 9 of 15 parallelizable |
| Phase 6 — US3 Notice Board | P2 | T070–T082 (13) | 8 of 13 parallelizable |
| Phase 7 — US4 Feedback | P2 | T083–T095 (13) | 7 of 13 parallelizable |
| Phase N — Polish | — | T096–T103 (8) | 6 of 8 parallelizable |
| **Total** | | **103 tasks** | **65 parallelizable** |
