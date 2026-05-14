# Phase 0 Research: Digital Reception Portal

**Feature**: Digital Reception Portal  
**Research Date**: 2026-05-14  
**Resolved**: All NEEDS CLARIFICATION items from Technical Context

---

## R-001: Frontend / Full-Stack Framework

**Decision**: Next.js 14 with TypeScript (App Router)  
**Rationale**: Next.js delivers server-side rendering for fast initial page loads
(critical for first-visit residents), file-based routing that maps cleanly to the
four portal pillars, built-in API routes that eliminate a separate backend service,
React Server Components that reduce client bundle size, and TypeScript for type
safety across shared data contracts. The App Router `(route-group)` convention
lets us co-locate role-specific pages `(resident)`, `(admin)`, `(elected-body)`,
and `(auth)` without duplication.  
**Alternatives considered**:  
- Vue 3 + separate Express API: two codebases, two deployments, higher maintenance.
- Plain HTML/PHP CMS (WordPress): insufficient for OTP auth, RBAC, live SSE, and
  booking conflict prevention.

---

## R-002: Database

**Decision**: PostgreSQL 15 via Prisma ORM; Neon managed PostgreSQL for hosting  
**Rationale**: The data model has strong relational requirements — RBAC joining
users to roles and module permission flags, booking slots with time-range conflict
checks, issue lifecycle state machine, and append-only audit logs. PostgreSQL row-
level locking and serializable transactions are required to prevent race conditions
in amenity booking (FR-007). JSONB supports the `permissions` column on `Role`
(module-level toggles) and `form_data` on `DigitalFormRequest` without a second
document store. Neon provides connection pooling, point-in-time recovery, and DB
branching (useful for staging environment) on a managed tier.  
**Alternatives considered**:  
- MySQL 8: similar feature set but weaker JSONB support and no native connection
  pooling without ProxySQL.
- MongoDB: document-store is poor fit for RBAC joins and time-range booking queries;
  transactions less ergonomic.
- Supabase: comparable managed PostgreSQL, slightly higher cost at scale.

---

## R-003: ORM

**Decision**: Prisma ORM  
**Rationale**: Type-safe schema-first ORM with auto-generated TypeScript client;
schema migrations handled by `prisma migrate`; introspection from existing DB
supported. Relations, enums, and JSONB columns are all first-class in Prisma schema
syntax. Works natively in Next.js server components and API routes.  
**Alternatives considered**:  
- Drizzle ORM: newer, slightly more lightweight; Prisma is more mature with better
  community support and documentation at the time of planning.
- TypeORM: class-decorator style is verbose and less idiomatic in functional TS.
- Raw SQL (pg): no schema migration tooling, more boilerplate.

---

## R-004: Authentication

**Decision**: NextAuth.js v5 (Auth.js) with custom credentials providers  
**Rationale**: NextAuth.js integrates directly into Next.js App Router via the
server handler. Two credentials strategies: (1) `OTPCredentialsProvider` — resident
submits mobile number, server sends OTP via MSG91, resident confirms code, session
created; (2) `PasswordCredentialsProvider` — mobile + password for fallback path.
JWT sessions with DB user look-up on each request. Role and verification_status
included in session token for RBAC middleware.  
**Alternatives considered**:  
- Auth0 / Clerk: managed auth — adds recurring cost and external GDPR/data-residency
  consideration for Indian user data; custom OTP flow harder to implement.
- Passport.js: lower-level, more boilerplate, not designed for App Router.

---

## R-005: OTP / SMS Gateway

**Decision**: MSG91 (primary), Twilio (fallback)  
**Rationale**: MSG91 is DLT-registered (Indian TRAI regulatory requirement for
transactional SMS) with official template registration support and competitive
pricing in India. API is simple HTTP REST. Twilio provides international fallback if
MSG91 SLA is missed; Twilio is pre-DLT-registered for transactional use.  
**Alternatives considered**:  
- Fast2SMS: lower cost but inconsistent delivery SLA; insufficient for authentication.
- WhatsApp Business API: higher friction for first-time login; requires WhatsApp
  account; introduces UI complexity.
- Email OTP: not suitable — spec requires mobile OTP as primary (FR-019).

---

## R-006: Payment Tracking

**Decision**: Razorpay (payment gateway integration for CAM + summer camp)  
**Rationale**: India's leading payment gateway; supports UPI, cards, net banking,
and wallets in one integration. SDK available for Next.js. Webhook events
`payment.captured` and `payment.failed` enable server-side payment confirmation
tracking without polling. Summer camp `payment_status` and schedule unlock flow
driven by webhook (FR-008). CAM payment redirects to Razorpay hosted page; status
confirmation captured on return + webhook for reliability.  
**Alternatives considered**:  
- PayU India: comparable but smaller ecosystem; fewer community integrations.
- Stripe: no native UPI support; not India-optimized.
- Cash / offline tracking: contradicts the "digital completion without manual
  intervention" requirement (SC-003).

---

## R-007: Real-Time Notice Delivery

**Decision**: Server-Sent Events (SSE) with 30-second polling fallback  
**Rationale**: Notices flow server → client only (uni-directional). SSE is the
simplest HTTP/2-native mechanism for this pattern; no separate WebSocket server
needed; browser reconnects automatically. The `/api/sse/notices` endpoint fans out
to all connected clients when a new NoticeItem is published. A 30-second polling
fallback (`/api/notices/active`) handles clients behind SSE-blocking proxies,
ensuring SC-004 (95% of notices visible within 60 s) is met via the polling path
even when SSE is unavailable.  
**Alternatives considered**:  
- WebSockets: bidirectional; overkill for read-only notice broadcast; requires
  stateful connection management (e.g., socket.io server) that adds complexity.
- Firebase Realtime Database / Pusher: external paid service; dependency justified
  only if SSE is insufficient — it is sufficient here.

---

## R-008: File Storage (Forms and Documents)

**Decision**: Cloudflare R2 (S3-compatible object storage)  
**Rationale**: PDF form templates (interiors work permission, vehicle sticker,
move-in/out) are static files served to residents. Signed URLs enforce access
control on protected documents. Cloudflare R2 has zero egress fees (unlike AWS S3),
CDN proximity to Indian users via Cloudflare's edge network, and S3-compatible API
(works with AWS SDK v3).  
**Alternatives considered**:  
- AWS S3: same feature set but egress charges; still acceptable if R2 not preferred.
- Filesystem on Vercel: not persistent across deployments; not suitable.
- Google Cloud Storage: similar feature set; more complex IAM than R2 for this use.

---

## R-009: RBAC Design

**Decision**: Database-backed RBAC with module-level permission JSONB per Role row  
**Rationale**: Three fixed roles: `resident`, `elected_body`, `admin`. Each Role
row carries a `permissions` JSONB object with module keys (`notices`, `bookings`,
`forms`, `issue_routing`, `user_management`, `content_management`), each holding
granular boolean flags (e.g., `{ "notices": { "read": true, "publish": false } }`).
Admin UI toggles update the Role row (FR-014). RBAC middleware in
`lib/permissions.ts` reads session role → looks up Role row → checks required
flag before allowing the API route handler to proceed. No third-party RBAC library
needed at this scale.  
**Alternatives considered**:  
- Casbin: powerful policy engine but over-engineered for 3 roles + ~10 modules.
- Per-user ACL: too granular; maintenance burden for community admin; contradicts
  the clarified module-level-per-role model.
- Hard-coded permission constants: can't be toggled by admin at runtime (FR-014).

---

## R-010: Audit Log Retention

**Decision**: Append-only `audit_log` PostgreSQL table (active 12 months) +
S3/R2 JSONL archive (months 13–24)  
**Rationale**: At ~1,462 units with moderate privileged actions (~50/day), an
audit_log table accrues ~18,000 rows/year — negligible for PostgreSQL. However, for
the 24-month retention requirement (FR-025), rows older than 12 months are exported
to a JSONL file on R2 via a scheduled nightly job and soft-deleted from the live
table. Authorized admins can retrieve archived audit records via a secure download
API. This keeps the live table lean for query performance while satisfying retention.  
**Alternatives considered**:  
- Keep all 24 months in PostgreSQL: functionally fine at this row volume; archival
  added for cost/performance hygiene as volume grows.
- External audit service (e.g., Datadog logs, Sumo Logic): adds external dependency
  and recurring cost for a feature PostgreSQL handles natively.

---

## R-011: Deployment

**Decision**: Vercel (Next.js hosting) + Neon PostgreSQL + Cloudflare R2  
**Rationale**: Vercel is the canonical production host for Next.js. Zero-config CI/CD
from git push; preview deployments per PR; edge network with points-of-presence
close to India. Neon provides the managed PostgreSQL with auto-scaling, connection
pooling (PgBouncer), and daily backups. R2 stores static PDFs and archived audit
logs.  
**Alternatives considered**:  
- Self-hosted VPS (DigitalOcean / Hetzner): lower unit cost but requires DevOps
  effort for SSL, backups, scaling — not suitable for a society-managed application.
- AWS ECS + RDS: full-featured but over-engineered for this community portal scale
  and team capability.
