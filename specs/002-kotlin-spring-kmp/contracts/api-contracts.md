# API Contracts: Platform Architecture — Kotlin/Spring Boot + Compose Multiplatform

**Phase**: 1 — Design  
**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)  
**Date**: 2026-05-14

All endpoints are served under the `/api/v1/` prefix (FR-014). All request and response bodies use `Content-Type: application/json`. All authenticated endpoints require `Authorization: Bearer <jwt>` header. Error responses use the shared `ApiError` type.

> **Note**: Endpoint shapes are inherited from [spec 001 api-contracts.md](../../001-digital-reception-portal/contracts/api-contracts.md). This document records the updated paths (with `/v1/` prefix), the Kotlin types from `shared/` that map to each endpoint, and any Spring Boot implementation notes.

---

## Authentication

### POST /api/v1/auth/otp/request
Request OTP to mobile number.

**Request body**: `OtpRequestDto`
```json
{ "mobileNumber": "+917890123456" }
```
**Response 200**: `{ "message": "OTP sent" }`  
**Response 429**: `ApiError(code="OTP_RATE_LIMIT_EXCEEDED")`  
**Spring**: `@PostMapping("/api/v1/auth/otp/request")` in `AuthController`; delegates to `OtpService` which calls MSG91 → Twilio fallback; rate-limited via Redis or in-memory `ConcurrentHashMap<mobile, Instant>`.

---

### POST /api/v1/auth/otp/verify
Verify OTP and issue JWT.

**Request body**: `OtpVerifyDto`  
**Response 200**: `AuthResponseDto` (includes JWT + `UserDto`)  
**Response 400**: `ApiError(code="OTP_INVALID")` | `ApiError(code="OTP_EXPIRED")`  
**Spring**: `AuthService.verifyOtp()` → `JwtService.generateToken(user)` → returns `AuthResponseDto`.

---

### POST /api/v1/auth/password/login
Admin/staff login with password.

**Request body**: `PasswordLoginDto`  
**Response 200**: `AuthResponseDto`  
**Response 401**: `ApiError(code="AUTH_INVALID_CREDENTIALS")`  
**Spring**: `AuthService.passwordLogin()` → `BCryptPasswordEncoder.matches()` → JWT.

---

## Users

### GET /api/v1/users/me
Get current authenticated user profile.

**Response 200**: `UserDto`  
**Auth**: Required.

---

### POST /api/v1/users/register
Register a new resident (unauthenticated).

**Request body**:
```json
{ "fullName": "Anita Sharma", "mobileNumber": "+917890123456", "flatNumber": "A101", "password": "..." }
```
**Response 201**: `UserDto` (status = UNVERIFIED)  
**Response 409**: `ApiError(code="USER_ALREADY_EXISTS")`  
**Spring**: Password hashed with `BCryptPasswordEncoder` before persist.

---

### PATCH /api/v1/users/{id}/approve
Approve a resident (Management role required).

**Response 200**: `UserDto` (status = APPROVED)  
**Auth**: `MANAGEMENT` or `ADMIN` role.

---

### PATCH /api/v1/users/{id}/reject
Reject a resident.

**Request body**: `{ "reason": "..." }`  
**Response 200**: `UserDto` (status = REJECTED)  
**Auth**: `MANAGEMENT` or `ADMIN` role.

---

### PATCH /api/v1/users/{id}/role
Assign role to user.

**Request body**: `{ "roleId": "uuid" }`  
**Response 200**: `UserDto`  
**Auth**: `ADMIN` role.

---

### GET /api/v1/users?status=UNVERIFIED&page=0&size=20
List users (paginated, filtered by verification status).

**Response 200**: `PagedResponse<UserDto>`  
**Auth**: `MANAGEMENT` or `ADMIN`.

---

## Permissions / Roles

### GET /api/v1/permissions/roles
List all roles.

**Response 200**: `List<RoleDto>`  
**Auth**: `ADMIN`.

---

### POST /api/v1/permissions/roles
Create a new role.

**Request body**: `RoleDto` (without id)  
**Response 201**: `RoleDto`  
**Auth**: `ADMIN`.

---

### PUT /api/v1/permissions/roles/{id}
Update role permissions.

**Request body**: `RoleDto`  
**Response 200**: `RoleDto`  
**Auth**: `ADMIN`.

---

## Notices

### GET /api/v1/notices?type=GENERAL&page=0&size=20
List published notices (sorted by pinned desc, publish_at desc).

**Response 200**: `PagedResponse<NoticeDto>`  
**Auth**: Required (any role).

---

### POST /api/v1/notices
Create a notice.

**Request body**: `NoticeDto` (without id, authorId, createdAt)  
**Response 201**: `NoticeDto`  
**Auth**: `MANAGEMENT`, `ELECTED_BODY`, or `ADMIN`.

---

### PUT /api/v1/notices/{id}
Update a notice.

**Response 200**: `NoticeDto`  
**Auth**: Author or `ADMIN`.

---

### DELETE /api/v1/notices/{id}
Delete a notice.

**Response 204**  
**Auth**: Author or `ADMIN`.

---

### GET /api/v1/sse/notices
Server-Sent Events stream for real-time notice updates.

**Response**: `text/event-stream`; each event is a JSON-serialized `NoticeDto`  
**Auth**: Required (Bearer token in query param `?token=` for SSE browser compatibility)  
**Spring**: `SseEmitter` registered per connection; emitter stored in a `CopyOnWriteArrayList`; fired on notice create/update/delete.

---

## Facility Bookings

### GET /api/v1/bookings/slots?facility=CLUBHOUSE&date=2026-05-20
List available slots for a facility on a given date.

**Response 200**: `List<FacilitySlotDto>`  
**Auth**: Required.

---

### POST /api/v1/bookings
Create a booking.

**Request body**: `BookingRequestDto`  
**Response 201**: `FacilitySlotDto` (status = BOOKED)  
**Response 409**: `ApiError(code="SLOT_ALREADY_BOOKED")`  
**Spring**: Uses `SELECT FOR UPDATE` inside `@Transactional` to prevent double-booking.

---

### DELETE /api/v1/bookings/{slotId}
Cancel a booking.

**Response 204**  
**Auth**: Booking owner or `ADMIN`.

---

## Summer Camp

### POST /api/v1/summer-camp/register
Register a child for summer camp.

**Request body**: `SummerCampRegistrationDto` (without id, paymentStatus, createdAt)  
**Response 201**: `SummerCampRegistrationDto`  
**Auth**: Resident.

---

### POST /api/v1/summer-camp/payment/order
Create Razorpay payment order.

**Response 200**: `{ "orderId": "...", "amount": 5000, "currency": "INR" }`  
**Auth**: Resident.

---

### POST /api/v1/summer-camp/payment/webhook
Razorpay webhook (unauthenticated; verified via `X-Razorpay-Signature` header).

**Response 200**  
**Spring**: `@PostMapping` with `@RequestHeader("X-Razorpay-Signature")`; HMAC-SHA256 verification before processing.

---

### GET /api/v1/summer-camp/schedule
Get schedule (only if payment confirmed).

**Response 200**: `{ "scheduleUrl": "https://..." }`  
**Response 403**: `ApiError(code="PAYMENT_REQUIRED")`  
**Auth**: Registered parent only.

---

## Digital Forms

### GET /api/v1/forms?type=GATE_PASS&status=SUBMITTED
List form requests.

**Response 200**: `PagedResponse<DigitalFormRequestDto>`  
**Auth**: Resident sees own; staff sees all for their module.

---

### POST /api/v1/forms
Submit a form request.

**Request body**: `DigitalFormRequestDto` (without id, submissionStatus, createdAt)  
**Response 201**: `DigitalFormRequestDto`  
**Auth**: Resident.

---

### PATCH /api/v1/forms/{id}/status
Update form status (staff action).

**Request body**: `{ "status": "APPROVED", "reviewNotes": "..." }`  
**Response 200**: `DigitalFormRequestDto`  
**Auth**: `MANAGEMENT` or `ADMIN`.

---

## Issues

### GET /api/v1/issues?status=NEW&category=ELECTRICAL
List issue tickets.

**Response 200**: `PagedResponse<IssueTicketDto>`  
**Auth**: Resident sees own; staff sees assigned team's.

---

### POST /api/v1/issues
Submit an issue ticket.

**Request body**: `IssueTicketDto` (without id, status, createdAt, updatedAt)  
**Response 201**: `IssueTicketDto`  
**Auth**: Resident.

---

### PATCH /api/v1/issues/{id}/status
Advance issue status.

**Request body**: `{ "status": "IN_PROGRESS", "notes": "..." }`  
**Response 200**: `IssueTicketDto`  
**Auth**: Assigned team or `ADMIN`.  
**Validation**: Valid FSM transitions only: NEW→ACKNOWLEDGED, ACKNOWLEDGED→IN_PROGRESS, IN_PROGRESS→RESOLVED, RESOLVED→CLOSED.

---

### GET /api/v1/issues/{id}
Get single issue ticket.

**Response 200**: `IssueTicketDto`  
**Auth**: Owner or staff.

---

## Feedback

### POST /api/v1/feedback
Submit feedback.

**Request body**: `FeedbackCreateDto`  
**Response 201**: `FeedbackSubmissionDto`  
**Auth**: Resident (optional for anonymous).

---

### GET /api/v1/feedback?category=MAINTENANCE&page=0&size=20
List feedback (management view).

**Response 200**: `PagedResponse<FeedbackSubmissionDto>`  
**Auth**: `MANAGEMENT` or `ADMIN`.

---

## Content (Info Desk)

### GET /api/v1/content/facility-timings
**Response 200**: `{ "timings": [ { "facility": "Gym", "hours": "06:00–22:00", ... } ] }`

---

### GET /api/v1/content/sops
**Response 200**: `{ "sops": [ { "title": "Fire Safety", "url": "...", "version": "2.1" } ] }`

---

### GET /api/v1/content/contacts
**Response 200**: `{ "contacts": [ { "name": "Security Desk", "phone": "...", "available24x7": true } ] }`

---

### GET /api/v1/content/faq
**Response 200**: `{ "faqs": [ { "question": "...", "answer": "...", "category": "..." } ] }`

---

## Audit Log

### GET /api/v1/audit?action=USER_APPROVED&page=0&size=20
List audit log entries (admin only).

**Response 200**: `PagedResponse<AuditLogDto>`  
**Auth**: `ADMIN`.

---

### GET /api/v1/audit/export?from=2026-01-01&to=2026-03-31
Export audit log as JSONL for archival.

**Response 200**: `application/x-ndjson` stream  
**Auth**: `ADMIN`.

---

## Health / Actuator

### GET /api/v1/health
Application health check (publicly accessible).

**Response 200**: `{ "status": "UP" }`  
**Spring**: Delegates to Spring Boot Actuator `/actuator/health`; re-exposed under versioned path.

---

## Error Response Shape

All error responses:

```json
{
  "code": "AUTH_INVALID_TOKEN",
  "message": "The provided token is invalid or has expired.",
  "field": null
}
```

HTTP status mapping:

| Status | Code prefix |
|---|---|
| 400 | `VALIDATION_*`, `OTP_*`, `BOOKING_*` |
| 401 | `AUTH_*` |
| 403 | `FORBIDDEN_*`, `PAYMENT_REQUIRED` |
| 404 | `NOT_FOUND_*` |
| 409 | `CONFLICT_*`, `*_ALREADY_EXISTS` |
| 429 | `RATE_LIMIT_*` |
| 500 | `INTERNAL_ERROR` |
