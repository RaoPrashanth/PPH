# Implementation Plan: Digital Reception Portal

**Branch**: `master` | **Date**: 2026-05-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-digital-reception-portal/spec.md`

## Summary

The Digital Reception Portal is the virtual front desk for Prestige Primrose Hills
(1,462 flats). It delivers four resident-facing pillars: **Information Desk**
(facility timings, SOPs, emergency contacts, FAQ search), **Concierge** (amenity
booking, summer camp registration, digital forms, quick actions), **Notice Board**
(real-time banners, EC bulletins, event countdown), and **Feedback Counter**
(suggestions, issue reporting with lifecycle tracking). All pillars are gated by
role-based access control covering residents, elected body members, and admins.
Residents authenticate via mobile OTP with a password fallback; new accounts require
admin approval after unit-number + mobile verification. Privileged-action audit logs
are retained for 24 months.

**Technical approach** (see `research.md` for rationale): Next.js 14 monolith on
TypeScript, PostgreSQL 15 via Prisma, MSG91 for OTP, Razorpay for payment tracking,
SSE for live notices, deployed to Vercel + Neon managed database.

## Technical Context

**Language/Version**: TypeScript 5.x / Node.js 20 LTS  
**Primary Dependencies**: Next.js 14 (full-stack), Prisma ORM, NextAuth.js v5,
Tailwind CSS, MSG91 (OTP), Razorpay (payment webhooks), Zod (validation)  
**Storage**: PostgreSQL 15 (primary relational store) + S3-compatible object storage
for uploaded PDFs and form templates  
**Testing**: Jest + React Testing Library (unit/component), Playwright (E2E)  
**Target Platform**: Web server (Vercel/Node.js) + modern browsers (Chrome, Safari,
Firefox, Edge); responsive mobile and desktop  
**Project Type**: web-service (full-stack web application — Next.js monolith with
App Router, server components, and API routes)  
**Performance Goals**: Core pages load under 2 s at P95; live notice delivery within
60 s of publish (SC-004); booking confirmation within 3 s of submission (SC-002)  
**Constraints**: Mobile + desktop responsive (320 px – 1440 px); 24-month audit log
retention with archival after 12 months; admin-gated resident onboarding; all FRs
met on first implementation pass  
**Scale/Scope**: ~1,462 residential units; estimated 3,000–5,000 registered users;
peak concurrent load ~200; ~25 screens / route groups; ~40 API endpoints

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-evaluated after Phase 1 design.*

### Resident Value Gate — PASS

| Capability | Resident Workflow | User Story |
|---|---|---|
| Information Desk (facility timings, SOPs, FAQs, contacts) | Self-service info without calling the office | US1 (P1) |
| Concierge (amenity booking, summer camp, forms, quick actions) | Complete requests digitally without paper | US2 (P1) |
| Notice Board (banner, bulletins, event countdown) | Stay informed on community updates | US3 (P2) |
| Feedback Counter (suggestions, issue reporting + lifecycle) | Structured channel for improvement and maintenance | US4 (P2) |
| RBAC + admin approval onboarding | Governance integrity enabling all above features safely | US5 (P1) |

No capabilities are planned outside these resident workflows.

### Privacy and Consent Gate — PASS

| Entity | Fields | Purpose | Access |
|---|---|---|---|
| User | mobile, flat_number, full_name, email (optional) | Community identity + OTP auth | Self + admin |
| User | password_hash | Optional login fallback | System-internal only |
| User | communication_consent | Resident preference for non-emergency updates | Self-managed (FR-016) |
| SummerCampRegistration | child_name, child_age, guardian_id | Camp enrollment only | Guardian + admin |
| IssueTicket | location_description, description | Maintenance routing | Submitter + ops team + admin |
| AuditLog | actor_id, action, ip_address | Governance accountability (FR-025) | Admin only; 24-month retention |

Data protection: HTTPS in transit; encrypted DB at rest (Neon TDE); no PII in
application logs. Emergency notices exempt from `communication_consent` per
spec assumption 4. Consent is reversible at any time via profile settings.

### Reliability Gate — PASS

| Failure Mode | Impact | Recovery Path |
|---|---|---|
| SSE connection drops | Live banner delayed | Browser auto-reconnects; 30 s polling fallback ensures SC-004 (60 s) |
| OTP delivery fails (MSG91) | Resident blocked at login | Twilio fallback SMS; password path available after first login |
| Double-booking race condition | Two residents confirm same slot | PostgreSQL row-level lock + unique constraint on (facility_name, start_time) when reserved |
| Issue routing notification fails | Team not alerted | Ticket is DB-committed first; notification is async and retried; ticket reference always returned to resident |
| Payment webhook missed | Schedule access not unlocked | Razorpay idempotency key + manual admin override; resident directed to support |

Time-sensitive notices include `publish_at` + `expires_at` controls (FR-010). Silent
message loss is prevented by SSE/polling dual delivery path (Principle III).

### Accessibility Gate — PASS

| Check | Approach |
|---|---|
| Keyboard navigation | All interactive elements reachable via Tab/Enter; no keyboard traps |
| Color contrast | Tailwind default palette enforces ≥ 4.5:1 contrast for text; custom tokens audited via axe-core in CI |
| Semantic structure | Next.js App Router with semantic HTML (`nav`, `main`, `section`, `h1–h3`) |
| Responsive layout | Tailwind responsive utilities; tested at 320 px (mobile), 768 px (tablet), 1280 px (desktop) |
| Readable language | Plain English; no jargon; form labels and error messages user-reviewed |

Usability check target: ≥ 95% residents complete P1 workflows on first attempt (SC-006).

### Observability and Simplicity Gate — PASS

| Critical Path | Observable Via |
|---|---|
| Authentication (OTP + password) | Server log: `auth.attempt`, `auth.success`, `auth.failure` with user_id + timestamp |
| Notice publish | AuditLog: `NOTICE_PUBLISHED`; server log: SSE fanout count |
| Booking creation | AuditLog: `BOOKING_CREATED`; server log: conflict check result |
| Resident approval / rejection | AuditLog: `USER_APPROVED` / `USER_REJECTED` with admin actor |
| Role / permission change | AuditLog: `ROLE_CHANGED` with previous_state + new_state JSONB |

**Architectural complexity**: Next.js monolith (single deployable); PostgreSQL
(single database); Prisma (single ORM layer). No microservices, no separate message
queue, no separate caching layer. SSE replaces WebSockets (simpler, unidirectional).
Object storage is the only added subsystem; justified by PDF form distribution
requirement. No alternatives were simpler while satisfying FR-009 (digital forms hub).

*Post-Phase-1 re-check*: No new complexity introduced by data model or contracts
design. All entities map directly to spec entities (Section 7 of spec.md).
Constitution gates remain PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-digital-reception-portal/
├── plan.md              # This file (/speckit.plan output)
├── research.md          # Phase 0 output (/speckit.plan)
├── data-model.md        # Phase 1 output (/speckit.plan)
├── quickstart.md        # Phase 1 output (/speckit.plan)
├── contracts/
│   └── api-contracts.md # Phase 1 output (/speckit.plan)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

### Source Code (repository root)

This is a Next.js full-stack web application. All source code lives in a single
deployable project at the repository root (no separate backend/frontend repos).

```text
src/
├── app/                          # Next.js App Router root
│   ├── (auth)/                   # Sign-in, sign-up, pending-approval pages
│   │   ├── login/
│   │   ├── register/
│   │   └── pending-approval/
│   ├── (resident)/               # Resident-facing pages (requires approved session)
│   │   ├── page.tsx              # Digital Reception home
│   │   ├── information/          # Facility timings, SOPs, contacts, FAQ
│   │   ├── concierge/            # Amenity booking, summer camp, forms
│   │   ├── notices/              # Banner, bulletins, event countdown
│   │   └── feedback/             # Suggestion box, issue reporting
│   ├── (admin)/                  # Admin panel (requires admin role)
│   │   ├── users/                # Resident approval queue, role management
│   │   ├── notices/              # Publish and manage notice items
│   │   ├── permissions/          # Module-level permission toggle UI
│   │   ├── audit/                # Audit log viewer
│   │   └── content/              # Facility timings, SOPs, contact management
│   ├── (elected-body)/           # Elected body panel (requires elected_body role)
│   │   ├── bulletins/            # Publish EC bulletins
│   │   └── issues/               # Issue routing and status management
│   └── api/                      # API route handlers
│       ├── auth/                  # NextAuth.js handlers
│       ├── users/
│       ├── notices/
│       ├── bookings/
│       ├── summer-camp/
│       ├── forms/
│       ├── feedback/
│       ├── issues/
│       ├── audit/
│       └── sse/                  # Server-Sent Events endpoint for live notices
├── components/                   # Reusable UI components
│   ├── ui/                       # Base design system (buttons, inputs, cards)
│   ├── layout/                   # Header, sidebar, nav components
│   ├── auth/                     # OTP input, login form components
│   ├── notices/                  # Banner, bulletin card, countdown components
│   ├── bookings/                 # Slot picker, confirmation card components
│   └── feedback/                 # Suggestion form, issue form, status badge components
├── lib/                          # Shared utilities
│   ├── auth.ts                   # NextAuth.js configuration and OTP logic
│   ├── db.ts                     # Prisma client singleton
│   ├── permissions.ts            # RBAC middleware and permission check helpers
│   ├── otp.ts                    # MSG91 + Twilio OTP dispatch
│   ├── sse.ts                    # SSE connection manager and fanout
│   ├── storage.ts                # S3/R2 object storage helpers
│   └── audit.ts                  # AuditLog write helpers
└── services/                     # Business logic (pure functions, no HTTP)
    ├── booking-service.ts        # Conflict prevention, slot reservation
    ├── issue-service.ts          # Lifecycle transitions, routing
    ├── notice-service.ts         # Publish/expire/fanout
    ├── user-service.ts           # Verification, approval workflow
    └── payment-service.ts        # Razorpay webhook handling

prisma/
├── schema.prisma                 # Full data model (see data-model.md)
└── migrations/                   # Generated migration files

public/
└── forms/                        # Static PDF form templates for download

tests/
├── unit/                         # Jest unit tests for services and lib
├── integration/                  # API route integration tests
└── e2e/                          # Playwright end-to-end tests for P1 user stories
```

**Structure Decision**: Single Next.js monolith at repository root. The App Router
`(route-group)` pattern co-locates page components with their role context
(`(resident)`, `(admin)`, `(elected-body)`, `(auth)`) while sharing one API layer
and one Prisma DB client. No separate backend service is needed at this scale.
Complexity tracking N/A — no constitution violations were identified.
