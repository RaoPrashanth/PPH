# Data Model: Platform Architecture — Kotlin/Spring Boot + Compose Multiplatform

**Phase**: 1 — Design  
**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Date**: 2026-05-14

This document defines the Kotlin type system that bridges the three project modules. It does not redefine the business entities from [spec 001 data-model.md](../001-digital-reception-portal/data-model.md) — it defines how those entities are represented in the shared module and mapped to JPA entities in the backend.

---

## Layer Overview

```
┌─────────────────────────────────────────────────────────┐
│  shared/commonMain   — DTOs, ApiError, ApiConstants     │
│  (Kotlin/JVM + Kotlin/Wasm)                             │
└───────────┬────────────────────────┬────────────────────┘
            │                        │
  ┌─────────▼──────────┐   ┌────────▼────────────────────┐
  │  backend/           │   │  frontend/composeApp         │
  │  JPA @Entity types  │   │  ViewModel + UI state types  │
  │  map FROM shared    │   │  deserialize TO shared DTOs  │
  │  DTOs via mappers   │   │  via Ktor + kotlinx.serial.  │
  └─────────────────────┘   └─────────────────────────────┘
```

**Key rule**: The shared module contains only `@Serializable` data classes and pure functions. It has no JPA annotations, no Spring annotations, no Compose annotations. Platform-specific code is confined to `jvmMain` and `wasmJsMain` source sets.

---

## Shared Module Types (`shared/src/commonMain`)

### Package: `com.pph.shared.dto`

#### UserDto
```kotlin
@Serializable
data class UserDto(
    val id: String,                          // UUID
    val fullName: String,
    val mobileNumber: String,
    val flatNumber: String,
    val verificationStatus: VerificationStatus,
    val roles: List<String>,                 // role names
    val notificationPreferences: NotificationPreferences,
    val createdAt: String                    // ISO-8601
)

@Serializable
enum class VerificationStatus {
    UNVERIFIED, MOBILE_MATCHED, APPROVED, REJECTED
}

@Serializable
data class NotificationPreferences(
    val smsEnabled: Boolean = true,
    val whatsappEnabled: Boolean = false,
    val consentGiven: Boolean = false,
    val consentTimestamp: String? = null     // ISO-8601
)
```

#### RoleDto
```kotlin
@Serializable
data class RoleDto(
    val id: String,
    val name: String,
    val permissions: Map<String, ModulePermissions>
)

@Serializable
data class ModulePermissions(
    val canView: Boolean = false,
    val canCreate: Boolean = false,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false
)
```

#### NoticeDto
```kotlin
@Serializable
data class NoticeDto(
    val id: String,
    val title: String,
    val body: String,
    val noticeType: NoticeType,
    val authorId: String,
    val authorName: String,
    val publishAt: String,                   // ISO-8601
    val expiresAt: String?,
    val attachmentUrl: String?,
    val isPinned: Boolean,
    val createdAt: String
)

@Serializable
enum class NoticeType {
    GENERAL, MAINTENANCE, EVENT, EMERGENCY, POLL
}
```

#### BookingDto / FacilitySlotDto
```kotlin
@Serializable
data class FacilitySlotDto(
    val id: String,
    val facilityName: String,
    val date: String,                        // ISO-8601 date
    val startTime: String,                   // HH:mm
    val endTime: String,
    val status: SlotStatus,
    val bookedByUserId: String?,
    val bookedByName: String?
)

@Serializable
enum class SlotStatus { AVAILABLE, BOOKED, BLOCKED }

@Serializable
data class BookingRequestDto(
    val slotId: String,
    val notes: String? = null
)
```

#### SummerCampDto
```kotlin
@Serializable
data class SummerCampRegistrationDto(
    val id: String,
    val childName: String,
    val childAge: Int,
    val parentUserId: String,
    val paymentStatus: PaymentStatus,
    val scheduleAccessUnlocked: Boolean,
    val registeredAt: String
)

@Serializable
enum class PaymentStatus { PENDING, PAID, FAILED, REFUNDED }
```

#### IssueTicketDto
```kotlin
@Serializable
data class IssueTicketDto(
    val id: String,
    val title: String,
    val description: String,
    val category: IssueCategory,
    val status: IssueStatus,
    val submittedByUserId: String,
    val assignedTeam: String?,
    val resolutionNotes: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class IssueCategory {
    ELECTRICAL, PLUMBING, LIFT, SECURITY, CLEANING, COMMON_AREA, OTHER
}

@Serializable
enum class IssueStatus {
    NEW, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED
}
```

#### FeedbackDto
```kotlin
@Serializable
data class FeedbackSubmissionDto(
    val id: String,
    val category: String,
    val rating: Int,                         // 1–5
    val comment: String?,
    val isAnonymous: Boolean,
    val submittedByUserId: String?,
    val createdAt: String
)

@Serializable
data class FeedbackCreateDto(
    val category: String,
    val rating: Int,
    val comment: String? = null,
    val isAnonymous: Boolean = false
)
```

#### DigitalFormRequestDto
```kotlin
@Serializable
data class DigitalFormRequestDto(
    val id: String,
    val formType: FormType,
    val submissionStatus: FormStatus,
    val submittedByUserId: String,
    val formData: Map<String, String>,       // form field key → value
    val reviewNotes: String?,
    val createdAt: String
)

@Serializable
enum class FormType {
    GATE_PASS, NOC_REQUEST, PARKING_ALLOTMENT, MAINTENANCE_REQUEST, OTHER
}

@Serializable
enum class FormStatus {
    SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, COMPLETED
}
```

#### AuditDto
```kotlin
@Serializable
data class AuditLogDto(
    val id: String,
    val action: AuditAction,
    val actorId: String,
    val actorName: String,
    val targetEntityType: String,
    val targetEntityId: String?,
    val metadata: Map<String, String>,
    val createdAt: String
)

@Serializable
enum class AuditAction {
    USER_REGISTERED, USER_APPROVED, USER_REJECTED, USER_ROLE_CHANGED,
    NOTICE_CREATED, NOTICE_UPDATED, NOTICE_DELETED,
    BOOKING_CREATED, BOOKING_CANCELLED,
    ISSUE_CREATED, ISSUE_STATUS_CHANGED,
    FEEDBACK_SUBMITTED
}
```

#### Auth DTOs
```kotlin
@Serializable
data class OtpRequestDto(val mobileNumber: String)

@Serializable
data class OtpVerifyDto(val mobileNumber: String, val otp: String)

@Serializable
data class PasswordLoginDto(val mobileNumber: String, val password: String)

@Serializable
data class AuthResponseDto(
    val token: String,                       // JWT access token
    val user: UserDto,
    val expiresIn: Long                      // seconds
)
```

#### Error DTO
```kotlin
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val field: String? = null
)
```

### Package: `com.pph.shared.constants`

#### ApiConstants
```kotlin
object ApiConstants {
    const val API_VERSION = "v1"
    const val BASE_PATH = "/api/$API_VERSION"
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100
    const val JWT_HEADER = "Authorization"
    const val JWT_PREFIX = "Bearer "
}
```

### Package: `com.pph.shared.validation`

#### Validators
```kotlin
object Validators {
    private val MOBILE_REGEX = Regex("""^\+91[6-9]\d{9}$""")
    private val FLAT_REGEX = Regex("""^[A-Z]\d{1,3}$""")

    fun isValidMobileNumber(mobile: String): Boolean =
        MOBILE_REGEX.matches(mobile)

    fun isValidFlatNumber(flat: String): Boolean =
        FLAT_REGEX.matches(flat.uppercase())

    fun isValidRating(rating: Int): Boolean =
        rating in 1..5

    fun isValidOtp(otp: String): Boolean =
        otp.length == 6 && otp.all { it.isDigit() }

    fun sanitizeText(input: String, maxLength: Int = 500): String =
        input.trim().take(maxLength)
}
```

---

## Backend JPA Entities (`backend/src/main/kotlin/com/pph/backend/`)

JPA entities are in `backend/` only — they are NOT in the shared module. Each entity maps 1:1 to the shared DTO via a mapper extension function.

### Mapping Pattern

```kotlin
// In backend: e.g., backend/.../users/UserMapper.kt
fun UserEntity.toDto(): UserDto = UserDto(
    id = this.id.toString(),
    fullName = this.fullName,
    mobileNumber = this.mobileNumber,
    flatNumber = this.flatNumber,
    verificationStatus = VerificationStatus.valueOf(this.verificationStatus.name),
    roles = this.roles.map { it.name },
    notificationPreferences = NotificationPreferences(
        smsEnabled = this.smsEnabled,
        whatsappEnabled = this.whatsappEnabled,
        consentGiven = this.consentGiven,
        consentTimestamp = this.consentTimestamp?.toString()
    ),
    createdAt = this.createdAt.toString()
)
```

### JPA Entity Structure (abbreviated — full schema in spec 001 data-model.md)

| JPA Entity | Table | Key Fields | Shared DTO |
|---|---|---|---|
| `UserEntity` | `users` | id (UUID), mobile_number, flat_number, verification_status, roles (ManyToMany) | `UserDto` |
| `RoleEntity` | `roles` | id, name, permissions (JSONB) | `RoleDto` |
| `NoticeItemEntity` | `notice_items` | id, title, body, notice_type, publish_at, expires_at | `NoticeDto` |
| `FacilitySlotEntity` | `facility_slots` | id, facility_name, date, start_time, status | `FacilitySlotDto` |
| `SummerCampRegistrationEntity` | `summer_camp_registrations` | id, child_name, payment_status, schedule_access_unlocked | `SummerCampRegistrationDto` |
| `IssueTicketEntity` | `issue_tickets` | id, title, category, status, assigned_team | `IssueTicketDto` |
| `FeedbackSubmissionEntity` | `feedback_submissions` | id, rating, is_anonymous | `FeedbackSubmissionDto` |
| `DigitalFormRequestEntity` | `digital_form_requests` | id, form_type, submission_status, form_data (JSONB) | `DigitalFormRequestDto` |
| `AuditLogEntity` | `audit_logs` | id, action, actor_id, metadata (JSONB) | `AuditLogDto` |

---

## Frontend UI State Types (`frontend/composeApp/src/commonMain`)

UI state types live in `commonMain` only — they are wrappers around shared DTOs that carry loading/error state for the Compose UI layer.

```kotlin
// Generic sealed UI state wrapper — used by all ViewModels
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: ApiError) : UiState<Nothing>()
}

// Example: NoticeListViewModel state
data class NoticeListScreenState(
    val notices: List<NoticeDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: ApiError? = null,
    val hasMore: Boolean = true
)
```

---

## Pagination Envelope

All paginated list endpoints use a shared envelope:

```kotlin
@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean
)
```

---

## Type Safety Guarantee

All types crossing the API boundary are:
1. Defined in `shared/commonMain` with `@Serializable`
2. Serialized/deserialized by `kotlinx.serialization` on both ends
3. Validated at compile time — a field rename breaks both backend and frontend simultaneously

This satisfies **SC-005** (zero duplicated DTO definitions) and the Privacy & Consent Gate (no untyped JSON).
