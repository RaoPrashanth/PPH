# Data Model: Digital Reception Portal

**Feature**: Digital Reception Portal  
**Date**: 2026-05-14  
**Source**: `spec.md` — Key Entities section; clarifications for RBAC, lifecycle, audit

---

## Entities

### 1. User *(Resident Account)*

Represents every authenticated member of the community regardless of role.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `flat_number` | VARCHAR(20) | No | Unit identifier (e.g., "A-1203"); used for verification |
| `mobile` | VARCHAR(15) UNIQUE | No | Primary identity and OTP target |
| `full_name` | VARCHAR(255) | No | Display name |
| `email` | VARCHAR(255) | Yes | Optional secondary contact |
| `password_hash` | VARCHAR(255) | Yes | Bcrypt hash for optional password fallback |
| `role_id` | UUID FK → Role | No | Assigned role; default `resident` |
| `verification_status` | ENUM | No | See state transitions below |
| `communication_consent` | BOOLEAN | No | Default `true`; resident-controlled |
| `created_at` | TIMESTAMPTZ | No | Account creation timestamp |
| `updated_at` | TIMESTAMPTZ | No | Last modification timestamp |

**ENUM `verification_status`**: `unverified` | `mobile_matched` | `approved` | `rejected`

**Validation rules**:
- `flat_number` + `mobile` must match verified resident records before account
  enters `mobile_matched` state (FR-022)
- Transition to `approved` / `rejected` requires admin role (FR-020)
- `communication_consent = false` suppresses non-emergency notices to this user;
  emergency notices bypass this flag (spec assumption 4)

**Indexes**: UNIQUE on `mobile`; index on `flat_number` for lookup during signup.

---

### 2. Role

Defines the permission capabilities for each role category.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `name` | ENUM UNIQUE | No | `resident` \| `elected_body` \| `admin` |
| `permissions` | JSONB | No | Module-level boolean permission flags |
| `created_at` | TIMESTAMPTZ | No | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | No | Last modified by admin |

**`permissions` JSONB schema**:

```json
{
  "notices":            { "read": true,  "publish": false, "delete": false },
  "bookings":           { "read": true,  "create": true,   "manage_all": false },
  "forms":              { "read": true,  "submit": true,   "manage": false },
  "issue_routing":      { "read": true,  "create": true,   "acknowledge": false, "manage": false },
  "user_management":    { "read": false, "approve": false, "manage_roles": false },
  "content_management": { "read": true,  "update": false }
}
```

**Default permissions by role**:

| Module flag | resident | elected_body | admin |
|---|---|---|---|
| `notices.read` | ✓ | ✓ | ✓ |
| `notices.publish` | — | ✓ | ✓ |
| `notices.delete` | — | — | ✓ |
| `bookings.read` | ✓ | ✓ | ✓ |
| `bookings.create` | ✓ | ✓ | ✓ |
| `bookings.manage_all` | — | — | ✓ |
| `forms.read` | ✓ | ✓ | ✓ |
| `forms.submit` | ✓ | ✓ | ✓ |
| `forms.manage` | — | — | ✓ |
| `issue_routing.read` | ✓ | ✓ | ✓ |
| `issue_routing.create` | ✓ | ✓ | ✓ |
| `issue_routing.acknowledge` | — | ✓ | ✓ |
| `issue_routing.manage` | — | ✓ | ✓ |
| `user_management.read` | — | — | ✓ |
| `user_management.approve` | — | — | ✓ |
| `user_management.manage_roles` | — | — | ✓ |
| `content_management.read` | ✓ | ✓ | ✓ |
| `content_management.update` | — | ✓ | ✓ |

Admins can change any flag via the Permissions UI (FR-014); changes are audited.

---

### 3. FacilitySlot

A bookable amenity time slot. One row per bookable unit of time.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `facility_name` | VARCHAR(100) | No | e.g., "Gym", "Badminton Court A", "Swimming Pool" |
| `start_time` | TIMESTAMPTZ | No | Slot start |
| `end_time` | TIMESTAMPTZ | No | Slot end; must be > start_time |
| `max_capacity` | INTEGER | No | Default 1 for exclusive-use facilities |
| `booked_by` | UUID FK → User | Yes | NULL when status = available |
| `status` | ENUM | No | `available` \| `reserved` \| `cancelled` |
| `confirmation_code` | VARCHAR(20) UNIQUE | Yes | Generated on booking; shown to resident |
| `created_at` | TIMESTAMPTZ | No | — |

**ENUM `status`**: `available` | `reserved` | `cancelled`

**Validation rules**:
- No two rows with the same `facility_name` and overlapping `(start_time, end_time)`
  may both have `status = reserved` (enforced via partial unique index + service-
  layer transaction with `SELECT FOR UPDATE`)
- Only users with `verification_status = approved` may book (FR-007)
- `confirmation_code` generated as ULID on successful reservation

---

### 4. SummerCampRegistration

One enrollment record per child per camp cycle.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `guardian_id` | UUID FK → User | No | Resident parent/guardian |
| `child_name` | VARCHAR(255) | No | Child's display name |
| `child_age` | INTEGER | No | Age at time of registration |
| `enrollment_status` | ENUM | No | `pending` \| `confirmed` \| `cancelled` |
| `payment_status` | ENUM | No | `unpaid` \| `paid` \| `refunded` |
| `payment_reference` | VARCHAR(100) | Yes | Razorpay order/payment ID |
| `schedule_access_unlocked` | BOOLEAN | No | Default false; set true on payment.captured webhook |
| `registered_at` | TIMESTAMPTZ | No | — |
| `updated_at` | TIMESTAMPTZ | No | — |

**Validation rules**:
- `schedule_access_unlocked` may only be set `true` when `payment_status = paid`
- `payment_reference` must be present before admin can manually override payment

---

### 5. DigitalFormRequest

Tracks resident submission of society forms requiring admin review.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `form_type` | ENUM | No | See values below |
| `applicant_id` | UUID FK → User | No | Submitting resident |
| `submission_status` | ENUM | No | `draft` \| `submitted` \| `under_review` \| `approved` \| `rejected` |
| `form_data` | JSONB | No | Submitted field values (schema varies by form_type) |
| `reviewed_by` | UUID FK → User | Yes | Admin who reviewed |
| `review_notes` | TEXT | Yes | Admin notes on approval/rejection |
| `submitted_at` | TIMESTAMPTZ | Yes | Null until resident submits |
| `reviewed_at` | TIMESTAMPTZ | Yes | Null until reviewed |
| `created_at` | TIMESTAMPTZ | No | — |

**ENUM `form_type`**: `interiors_work_permission` | `vehicle_sticker_request` |
`move_in_out_approval` | `party_hall_booking` | `visitor_parking_pass` | `other`

**Validation rules**:
- `submission_status` transitions: `draft → submitted → under_review → approved/rejected`
- `reviewed_by` must have `forms.manage = true` permission

---

### 6. NoticeItem

Time-bounded community announcement. Drives live banner, EC bulletins, and event countdown.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `notice_type` | ENUM | No | `urgent_banner` \| `ec_bulletin` \| `event_countdown` |
| `title` | VARCHAR(255) | No | Short headline |
| `body` | TEXT | No | Full message body |
| `event_date` | TIMESTAMPTZ | Yes | For `event_countdown` only; target event date |
| `published_by` | UUID FK → User | No | Authorized publisher |
| `publish_at` | TIMESTAMPTZ | No | When notice becomes active |
| `expires_at` | TIMESTAMPTZ | Yes | NULL = no automatic expiry |
| `is_active` | BOOLEAN | No | Default true; set false on expiry or manual retraction |
| `created_at` | TIMESTAMPTZ | No | — |
| `updated_at` | TIMESTAMPTZ | No | — |

**Validation rules**:
- `expires_at` must be > `publish_at` when set
- `published_by` must have `notices.publish = true`
- A scheduled job sets `is_active = false` when `expires_at` is reached
- SSE fanout triggered on INSERT or `is_active` change

---

### 7. FeedbackSubmission

Captures resident suggestions for community improvement.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `submitted_by` | UUID FK → User | No | Resident submitter |
| `category` | ENUM | No | `suggestion` \| `general_feedback` |
| `message` | TEXT | No | Submission content |
| `acknowledgement_status` | ENUM | No | `received` \| `reviewed` \| `actioned` |
| `reviewed_by` | UUID FK → User | Yes | Admin/elected-body reviewer |
| `review_notes` | TEXT | Yes | Internal notes |
| `submitted_at` | TIMESTAMPTZ | No | — |
| `updated_at` | TIMESTAMPTZ | No | — |

---

### 8. IssueTicket

Maintenance incident with full lifecycle tracking.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `submitted_by` | UUID FK → User | No | Reporting resident |
| `location_description` | VARCHAR(500) | No | Free-text location detail |
| `category` | ENUM | No | See values below |
| `severity` | ENUM | No | `low` \| `medium` \| `high` \| `critical`; default `medium` |
| `description` | TEXT | No | Issue details |
| `assigned_team` | VARCHAR(100) | Yes | Derived from category; shown to resident (FR-012) |
| `lifecycle_status` | ENUM | No | See state machine below |
| `resolution_notes` | TEXT | Yes | Populated on Resolved/Closed |
| `created_at` | TIMESTAMPTZ | No | — |
| `updated_at` | TIMESTAMPTZ | No | — |

**ENUM `category`**: `electrical` | `plumbing` | `civil_structure` | `common_area` |
`security` | `housekeeping` | `other`

**ENUM `lifecycle_status`**: `new` | `acknowledged` | `in_progress` | `resolved` | `closed`

**Default team routing by category**:

| Category | Assigned Team |
|---|---|
| electrical | Electrical Team |
| plumbing | Plumbing Team |
| civil_structure | Civil / Maintenance Team |
| common_area | Estate Management |
| security | Security Team |
| housekeeping | Housekeeping Team |
| other | Estate Manager |

**Lifecycle state machine** (forward-only transitions):

```
new → acknowledged → in_progress → resolved → closed
```

`closed` is terminal. No backward transitions permitted. Transition timestamps
stored via AuditLog (ISSUE_STATUS_CHANGED action).

---

### 9. AuditLog

Immutable record of privileged actions. Append-only.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID PK | No | Surrogate key |
| `actor_id` | UUID FK → User | No | User who performed the action |
| `action` | VARCHAR(100) | No | Action constant (see catalog below) |
| `target_type` | VARCHAR(50) | Yes | Entity name, e.g., `User`, `NoticeItem`, `Role` |
| `target_id` | UUID | Yes | PK of the affected entity |
| `previous_state` | JSONB | Yes | Snapshot before change (sensitive fields redacted) |
| `new_state` | JSONB | Yes | Snapshot after change |
| `ip_address` | INET | Yes | Request origin |
| `performed_at` | TIMESTAMPTZ | No | UTC timestamp; immutable after insert |

**Action catalog**:

| Action | Trigger |
|---|---|
| `USER_REGISTERED` | New signup submitted |
| `USER_APPROVED` | Admin approves account |
| `USER_REJECTED` | Admin rejects account |
| `USER_ROLE_CHANGED` | Role reassigned |
| `PERMISSION_UPDATED` | Module permission flag changed on a Role |
| `NOTICE_PUBLISHED` | NoticeItem created and activated |
| `NOTICE_RETRACTED` | NoticeItem set is_active = false before expiry |
| `BOOKING_CREATED` | FacilitySlot reserved |
| `BOOKING_CANCELLED` | Reservation cancelled |
| `FORM_REVIEWED` | DigitalFormRequest approved or rejected |
| `ISSUE_STATUS_CHANGED` | IssueTicket lifecycle transition |
| `CONTENT_UPDATED` | Facility timing, SOP, or emergency contact modified |

**Retention**: Active rows retained for 12 months in PostgreSQL; rows aged 12–24 months
exported to JSONL on R2 and soft-deleted from the live table via nightly archive job.
Authorized admins retrieve archived records via a signed download API.

---

## Entity Relationships

```
User ────────────── Role                    (many-to-one)
User ─(booked_by)── FacilitySlot            (one-to-many)
User ─(guardian)─── SummerCampRegistration  (one-to-many)
User ─(applicant)── DigitalFormRequest      (one-to-many)
User ─(published)── NoticeItem              (one-to-many)
User ─(submitted)── FeedbackSubmission      (one-to-many)
User ─(submitted)── IssueTicket             (one-to-many)
User ─(actor)─────── AuditLog              (one-to-many)
```

---

## State Transition Summary

### User.verification_status

```
unverified
    │  (unit+mobile match found — FR-022)
    ▼
mobile_matched
    │                       │
    │ (admin approves)       │ (admin rejects)
    ▼                       ▼
approved               rejected
                            │
                            │ (admin resets for retry)
                            ▼
                      mobile_matched
```

### IssueTicket.lifecycle_status

```
new → acknowledged → in_progress → resolved → closed
                                              (terminal)
```

### FacilitySlot.status

```
available ──(booking created)──► reserved
    ▲                                │
    └──(booking cancelled)───────────┘
                                     │
                      (admin force-cancel)
                                     ▼
                                 cancelled
```

### DigitalFormRequest.submission_status

```
draft → submitted → under_review → approved
                                 → rejected
```
