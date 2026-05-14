# API Contracts: Digital Reception Portal

**Feature**: Digital Reception Portal  
**Date**: 2026-05-14  
**Base URL**: `/api` (Next.js API routes)  
**Auth**: Session cookie via NextAuth.js; all endpoints except `/auth/*` and public
content endpoints require a valid session. Role checks are enforced at the handler
level via `lib/permissions.ts`.

**Common response envelopes**:

```ts
// Success
{ "data": <payload>, "meta"?: { "total"?: number, "page"?: number } }

// Error
{ "error": { "code": string, "message": string } }
```

**Common HTTP status codes**:

| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 400 | Validation error (Zod parse failure) |
| 401 | Unauthenticated |
| 403 | Insufficient permissions |
| 404 | Resource not found |
| 409 | Conflict (e.g., double-booking) |
| 500 | Internal server error |

---

## Authentication (`/api/auth`)

Handled by NextAuth.js v5. Custom endpoints documented below.

### POST `/api/auth/otp/request`

Request an OTP for login or sign-up.

**Request body**:

```json
{ "mobile": "9876543210" }
```

**Response 200**:

```json
{ "data": { "expires_in_seconds": 120 } }
```

**Response 429**: Rate-limited (max 3 OTP requests per mobile per 10 minutes).

---

### POST `/api/auth/otp/verify`

Verify OTP and establish session.

**Request body**:

```json
{ "mobile": "9876543210", "otp": "482910" }
```

**Response 200** (session cookie set):

```json
{ "data": { "user_id": "uuid", "role": "resident", "verification_status": "approved" } }
```

**Response 401**: Invalid or expired OTP.

---

### POST `/api/auth/password/login`

Password fallback login.

**Request body**:

```json
{ "mobile": "9876543210", "password": "..." }
```

**Response 200**: Same as OTP verify.  
**Response 401**: Invalid credentials.

---

## User Management (`/api/users`)

### POST `/api/users/register`

New resident sign-up (pre-approval).

**Permission**: Public (no session required).  
**Request body**:

```json
{
  "flat_number": "A-1203",
  "mobile": "9876543210",
  "full_name": "Priya Sharma",
  "email": "priya@example.com"
}
```

**Flow**: Server checks `flat_number + mobile` against resident records (FR-022).
If match found → creates User with `verification_status = mobile_matched`. If no
match → responds 409 with `UNVERIFIED_IDENTITY` code (FR-023).

**Response 201**:

```json
{ "data": { "user_id": "uuid", "verification_status": "mobile_matched" } }
```

**Response 409** (`UNVERIFIED_IDENTITY`):

```json
{ "error": { "code": "UNVERIFIED_IDENTITY", "message": "Unit number and mobile do not match resident records." } }
```

---

### GET `/api/users/me`

Fetch own profile.

**Permission**: Any authenticated user.  
**Response 200**:

```json
{
  "data": {
    "id": "uuid",
    "flat_number": "A-1203",
    "full_name": "Priya Sharma",
    "email": "priya@example.com",
    "role": "resident",
    "verification_status": "approved",
    "communication_consent": true
  }
}
```

---

### PATCH `/api/users/me`

Update own profile and consent.

**Permission**: Any authenticated approved user.  
**Request body** (all fields optional):

```json
{ "email": "new@example.com", "communication_consent": false }
```

**Response 200**: Updated User profile (same shape as GET `/me`).

---

### GET `/api/users` *(admin)*

List all users (paginated).

**Permission**: `user_management.read = true`  
**Query params**: `?page=1&limit=20&status=mobile_matched`  
**Response 200**:

```json
{
  "data": [ { "id": "uuid", "flat_number": "A-1203", "full_name": "...", "verification_status": "mobile_matched", "role": "resident" } ],
  "meta": { "total": 14, "page": 1 }
}
```

---

### POST `/api/users/:id/approve` *(admin)*

Approve a pending resident account (FR-020).

**Permission**: `user_management.approve = true`  
**Response 200**: Updated user with `verification_status = approved`.  
**Audit**: `USER_APPROVED` written to AuditLog.

---

### POST `/api/users/:id/reject` *(admin)*

Reject a pending resident account.

**Permission**: `user_management.approve = true`  
**Request body**:

```json
{ "reason": "No matching record for this unit." }
```

**Response 200**: Updated user with `verification_status = rejected`.  
**Audit**: `USER_REJECTED` written to AuditLog.

---

### PATCH `/api/users/:id/role` *(admin)*

Change a user's role.

**Permission**: `user_management.manage_roles = true`  
**Request body**:

```json
{ "role": "elected_body" }
```

**Response 200**: Updated user.  
**Audit**: `USER_ROLE_CHANGED` with `previous_state` and `new_state`.

---

## Permissions (`/api/permissions`)

### GET `/api/permissions/roles`

List all Role rows with current permissions.

**Permission**: `user_management.read = true`  
**Response 200**:

```json
{ "data": [ { "id": "uuid", "name": "resident", "permissions": { ... } } ] }
```

---

### PATCH `/api/permissions/roles/:role_name`

Update permission flags for a role (FR-014).

**Permission**: `user_management.manage_roles = true`  
**Request body** (partial; only supplied keys updated):

```json
{ "permissions": { "notices": { "publish": true } } }
```

**Response 200**: Full updated Role object.  
**Audit**: `PERMISSION_UPDATED` with diff.

---

## Notices (`/api/notices`)

### GET `/api/notices/active`

Fetch currently active notices for the portal.

**Permission**: Any authenticated approved user.  
**Query params**: `?type=urgent_banner|ec_bulletin|event_countdown`  
**Response 200**:

```json
{
  "data": [
    {
      "id": "uuid",
      "notice_type": "urgent_banner",
      "title": "Pool closed for maintenance",
      "body": "Pool will be closed 15–16 May for resurfacing.",
      "publish_at": "2026-05-14T08:00:00Z",
      "expires_at": "2026-05-16T18:00:00Z"
    }
  ]
}
```

---

### POST `/api/notices` *(authorized publisher)*

Create and publish a notice (FR-010).

**Permission**: `notices.publish = true`  
**Request body**:

```json
{
  "notice_type": "ec_bulletin",
  "title": "AGM Minutes — April 2026",
  "body": "...",
  "publish_at": "2026-05-14T10:00:00Z",
  "expires_at": null
}
```

**Response 201**: Created NoticeItem.  
**Side-effect**: SSE fanout to all connected clients.  
**Audit**: `NOTICE_PUBLISHED`.

---

### DELETE `/api/notices/:id` *(admin)*

Retract a notice before expiry.

**Permission**: `notices.delete = true`  
**Response 200**: `{ "data": { "retracted": true } }`  
**Audit**: `NOTICE_RETRACTED`.

---

### GET `/api/sse/notices`

Server-Sent Events stream for live notice updates (FR-010, SC-004).

**Permission**: Any authenticated approved user.  
**Content-Type**: `text/event-stream`  
**Events**:

```
event: notice_published
data: { "id": "uuid", "notice_type": "urgent_banner", "title": "...", "body": "..." }

event: notice_retracted
data: { "id": "uuid" }
```

Client should fall back to polling `/api/notices/active` every 30 seconds if SSE
connection is unavailable.

---

## Amenity Booking (`/api/bookings`)

### GET `/api/bookings/slots`

List available facility slots.

**Permission**: Any authenticated approved user.  
**Query params**: `?facility=Gym&date=2026-05-20`  
**Response 200**:

```json
{
  "data": [
    { "id": "uuid", "facility_name": "Gym", "start_time": "2026-05-20T06:00:00Z", "end_time": "2026-05-20T07:00:00Z", "status": "available" }
  ]
}
```

---

### POST `/api/bookings` *(resident)*

Reserve an amenity slot (FR-007).

**Permission**: `bookings.create = true` + `verification_status = approved`  
**Request body**:

```json
{ "slot_id": "uuid" }
```

**Flow**: Service layer acquires `SELECT FOR UPDATE` on slot row, verifies status =
`available`, sets `status = reserved`, `booked_by = current user`, generates
`confirmation_code`. Rolls back on conflict (409).

**Response 201**:

```json
{ "data": { "slot_id": "uuid", "confirmation_code": "GYM-20MAY-0006A", "start_time": "...", "end_time": "..." } }
```

**Response 409** (`SLOT_UNAVAILABLE`).  
**Audit**: `BOOKING_CREATED`.

---

### DELETE `/api/bookings/:slot_id` *(resident or admin)*

Cancel a booking.

**Permission**: Resident can cancel own booking; admin requires `bookings.manage_all = true`.  
**Response 200**: `{ "data": { "cancelled": true } }`  
**Audit**: `BOOKING_CANCELLED`.

---

## Summer Camp (`/api/summer-camp`)

### POST `/api/summer-camp/register`

Register a child for summer camp (FR-008).

**Permission**: Any authenticated approved user.  
**Request body**:

```json
{ "child_name": "Arjun Sharma", "child_age": 10 }
```

**Response 201**:

```json
{ "data": { "registration_id": "uuid", "enrollment_status": "pending", "payment_status": "unpaid" } }
```

---

### POST `/api/summer-camp/payment/order`

Create a Razorpay order for camp fee.

**Permission**: Owner of registration.  
**Request body**: `{ "registration_id": "uuid" }`  
**Response 200**: `{ "data": { "razorpay_order_id": "order_xxx", "amount_paise": 250000 } }`

---

### POST `/api/summer-camp/payment/webhook`

Razorpay webhook receiver for payment events.

**Auth**: Razorpay webhook signature (not session-based; verified via HMAC).  
**Flow**: On `payment.captured` → set `payment_status = paid`,
`schedule_access_unlocked = true`, `enrollment_status = confirmed`.

**Response 200**: `{ "received": true }`

---

### GET `/api/summer-camp/schedule`

Fetch camp schedule for a confirmed + paid registration.

**Permission**: Owner with `schedule_access_unlocked = true`, or admin.  
**Response 200**: Schedule data (JSON).

---

## Digital Forms (`/api/forms`)

### GET `/api/forms/templates`

List downloadable form templates.

**Permission**: Any authenticated approved user.  
**Response 200**:

```json
{
  "data": [
    { "form_type": "interiors_work_permission", "label": "Interiors Work Permission", "download_url": "https://cdn.example.com/forms/interiors.pdf" }
  ]
}
```

---

### POST `/api/forms/submit`

Submit a digital form request (FR-009).

**Permission**: `forms.submit = true`  
**Request body**:

```json
{
  "form_type": "vehicle_sticker_request",
  "form_data": { "vehicle_number": "KA01AB1234", "type": "four_wheeler" }
}
```

**Response 201**:

```json
{ "data": { "request_id": "uuid", "submission_status": "submitted" } }
```

---

### PATCH `/api/forms/:id/review` *(admin)*

Approve or reject a form request.

**Permission**: `forms.manage = true`  
**Request body**:

```json
{ "decision": "approved", "review_notes": "Verified; sticker ready for collection." }
```

**Response 200**: Updated DigitalFormRequest.  
**Audit**: `FORM_REVIEWED`.

---

## Feedback (`/api/feedback`)

### POST `/api/feedback`

Submit a suggestion or feedback (FR-011).

**Permission**: Any authenticated approved user.  
**Request body**:

```json
{ "category": "suggestion", "message": "Please add more badminton slots on weekends." }
```

**Response 201**: `{ "data": { "submission_id": "uuid", "acknowledgement_status": "received" } }`

---

## Issues (`/api/issues`)

### POST `/api/issues`

Report a maintenance issue (FR-011, FR-012).

**Permission**: Any authenticated approved user.  
**Request body**:

```json
{
  "category": "plumbing",
  "severity": "high",
  "location_description": "Block B, Level 3 corridor, tap leaking",
  "description": "Water dripping continuously from corridor tap. Started 13 May."
}
```

**Response 201**:

```json
{ "data": { "ticket_id": "uuid", "lifecycle_status": "new", "assigned_team": "Plumbing Team" } }
```

---

### GET `/api/issues/mine`

Fetch own reported issues.

**Permission**: Any authenticated approved user.  
**Response 200**: Paginated list of IssueTicket rows.

---

### GET `/api/issues` *(ops / admin)*

Fetch all issues (filterable).

**Permission**: `issue_routing.manage = true`  
**Query params**: `?status=new&category=plumbing&page=1`  
**Response 200**: Paginated list.

---

### PATCH `/api/issues/:id/status`

Advance lifecycle status (FR-024).

**Permission**: `issue_routing.acknowledge = true` (elected body / admin).  
**Request body**:

```json
{ "status": "acknowledged", "notes": "Plumbing team notified." }
```

**Validation**: Only forward transitions permitted; rejected otherwise (400).  
**Response 200**: Updated IssueTicket.  
**Audit**: `ISSUE_STATUS_CHANGED`.

---

## Content Management (`/api/content`)

### GET `/api/content/facility-timings`

Fetch current facility operating hours.

**Permission**: Any authenticated user (or public).  
**Response 200**: JSON array of facility timing entries.

---

### PATCH `/api/content/facility-timings` *(admin/elected body)*

Update facility timings.

**Permission**: `content_management.update = true`  
**Response 200**: Updated timings.  
**Audit**: `CONTENT_UPDATED`.

---

### GET `/api/content/sops`

Fetch SOP documents list with download links.

**Permission**: Any authenticated user.

---

### GET `/api/content/emergency-contacts`

Fetch emergency contacts.

**Permission**: Any authenticated user (or public).

---

### PATCH `/api/content/emergency-contacts` *(admin)*

Update emergency contact entries.

**Permission**: `content_management.update = true`  
**Audit**: `CONTENT_UPDATED`.

---

### GET `/api/content/faq`

Search or list FAQs.

**Permission**: Any authenticated user.  
**Query params**: `?q=parking`

---

## Audit Log (`/api/audit`)

### GET `/api/audit`

Fetch paginated audit log.

**Permission**: `user_management.read = true` (admin only).  
**Query params**: `?actor_id=uuid&action=USER_APPROVED&from=2026-01-01&to=2026-05-14&page=1&limit=50`  
**Response 200**:

```json
{
  "data": [
    {
      "id": "uuid",
      "actor": { "id": "uuid", "full_name": "Admin User", "flat_number": "MGMT-01" },
      "action": "USER_APPROVED",
      "target_type": "User",
      "target_id": "uuid",
      "performed_at": "2026-05-14T09:30:00Z"
    }
  ],
  "meta": { "total": 234, "page": 1 }
}
```

---

### GET `/api/audit/archive/:year/:month`

Download archived audit log (months 13–24) as JSONL.

**Permission**: Admin only.  
**Response 200**: Redirects to a signed R2 download URL (valid 60 seconds).
