# Feature Specification: Digital Reception Portal

**Feature Branch**: `[001-digital-reception-portal]`  
**Created**: 2026-05-14  
**Status**: Draft  
**Input**: User description: "A Digital Reception for Prestige Primrose Hills with information desk, concierge bookings/requests, real-time notice board, and feedback counter with role/permission management."

## Clarifications

### Session 2026-05-14

- Q: Which permission model should govern resident, elected-body, and admin capabilities? → A: Role-based access with module-level permission toggles per role.
- Q: Which resident authentication approach should be used? → A: Mobile OTP with optional password fallback, plus admin approval required before resident access is activated.
- Q: How should new resident identity be verified before admin approval? → A: Match unit number and registered mobile first, then admin approves activation.
- Q: Which issue-report lifecycle should be used? → A: New -> Acknowledged -> In Progress -> Resolved -> Closed.
- Q: What is the audit-log retention period for privileged actions? → A: 24 months.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Access Community Information Instantly (Priority: P1)

As a resident, I want to quickly find verified society information at any time so I do not need to call the management office for routine questions.

**Why this priority**: This is the highest-volume need and delivers immediate value to all residents, including first-time users.

**Independent Test**: Can be fully tested by searching and opening facility timings, SOPs, emergency contacts, and FAQs from the portal and confirming all required details are reachable without staff intervention.

**Acceptance Scenarios**:

1. **Given** a resident opens the Digital Reception page, **When** they select Facility Timings, **Then** they can view current operating hours for gym, pool, and sports areas.
2. **Given** a resident needs process guidance, **When** they open SOPs, **Then** they can view current procedures for party hall booking, move-in/move-out, and visitor parking.
3. **Given** a resident needs urgent help, **When** they open Emergency Contacts, **Then** they can see one-tap contact options for security gate, estate manager, plumber, and electrician.
4. **Given** a resident has a question, **When** they search the knowledge base, **Then** matching FAQ answers are shown in clear, readable format.

---

### User Story 2 - Complete Core Requests Digitally (Priority: P1)

As a resident or parent, I want to complete bookings and request workflows online so I can avoid paper forms and manual back-and-forth.

**Why this priority**: This turns the portal into an operational tool and directly reduces management workload and resident friction.

**Independent Test**: Can be fully tested by completing an amenity booking, a summer camp registration flow, and a form download/submission journey without using offline channels.

**Acceptance Scenarios**:

1. **Given** a resident wants to use a facility, **When** they open Amenity Booking, **Then** they can reserve an available slot and receive immediate booking confirmation.
2. **Given** a parent wants to enroll a child, **When** they use Summer Camp services, **Then** they can register, complete payment, and download the schedule from one place.
3. **Given** a resident needs administrative approval, **When** they access Digital Forms, **Then** they can find and submit required forms such as interiors work permission or vehicle sticker request.
4. **Given** a resident lands on the portal home, **When** they view Quick Actions, **Then** they can start Pay CAM, Book Court, Summer Camp, and Register Complaint in one tap.

---

### User Story 3 - Stay Updated with Real-Time Community Notices (Priority: P2)

As a resident, I want live updates and governance notices in one place so I can stay informed about operational changes and events.

**Why this priority**: Time-sensitive updates and transparent governance improve trust and reduce confusion across a large community.

**Independent Test**: Can be fully tested by publishing a live status item, an EC bulletin, and an event countdown and verifying they display correctly to residents.

**Acceptance Scenarios**:

1. **Given** management posts an urgent operations notice, **When** residents visit the portal, **Then** the live status banner displays the message prominently.
2. **Given** elected committee members publish governance decisions, **When** residents open EC Bulletins, **Then** the latest bulletins are visible in chronological order.
3. **Given** a community event is scheduled, **When** residents view the event section, **Then** they can see a days-to-go countdown for the next event.

---

### User Story 4 - Submit Suggestions and Report Issues (Priority: P2)

As a resident, I want a structured feedback channel so suggestions and maintenance issues reach the right team with traceability.

**Why this priority**: Capturing resident voice and maintenance issues systematically increases service quality and response accountability.

**Independent Test**: Can be fully tested by submitting a suggestion and a maintenance issue, then confirming each receives a tracking reference and route-to-team status.

**Acceptance Scenarios**:

1. **Given** a resident has an improvement idea, **When** they submit the suggestion box form, **Then** the suggestion is recorded with acknowledgement.
2. **Given** a resident notices an infrastructure issue, **When** they file an issue report, **Then** the report is routed to the technical team with a status reference.

---

### User Story 5 - Manage Roles and Privileged Permissions (Priority: P1)

As an elected body member or designated admin, I want role-based permissions so only authorized users can publish notices, manage operations, and access privileged functions.

**Why this priority**: Permission control is foundational for governance integrity and prevents unauthorized changes in a high-density community platform.

**Independent Test**: Can be fully tested by verifying resident, elected body, and admin accounts each see and perform only allowed actions.

**Acceptance Scenarios**:

1. **Given** a resident account, **When** the user accesses privileged management functions, **Then** those actions are blocked and not exposed.
2. **Given** an elected body member account, **When** the user manages bulletins and policy updates, **Then** authorized governance actions are available.
3. **Given** an admin account, **When** the user performs permission administration, **Then** role assignment and audit visibility are available according to policy.

### Edge Cases

- Two residents attempt to reserve the same amenity slot at nearly the same time.
- A live status message reaches its expiry time while residents are actively browsing.
- A resident submits duplicate issue reports for the same location/problem.
- An elected member role is revoked while the user is in an active session.
- Contact information changes after residents have bookmarked older details.
- A resident revokes communication consent after subscribing to updates.
- A newly registered user attempts access before admin approval is completed.
- A signup attempt uses a unit number and mobile that do not match resident records.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a Digital Reception home page with clearly discoverable sections for Information Desk, Concierge, Notice Board, and Feedback Counter.
- **FR-002**: System MUST provide facility timings for key amenities and show the latest approved schedule to residents.
- **FR-003**: System MUST provide SOP content for party hall booking, move-in/move-out, and visitor parking.
- **FR-004**: System MUST provide one-tap emergency contacts for key support roles and allow contact details to be updated by authorized users.
- **FR-005**: System MUST provide a searchable knowledge base for resident FAQs.
- **FR-006**: System MUST provide quick action entry points for Pay CAM, Book Court, Summer Camp, and Register Complaint.
- **FR-007**: System MUST support amenity booking with conflict prevention and resident-facing confirmation.
- **FR-008**: System MUST support summer camp registration, fee payment confirmation tracking, and schedule access for registered participants.
- **FR-009**: System MUST provide a digital forms hub where residents can download and/or submit official society forms.
- **FR-010**: System MUST display a real-time notice area including urgent status banners, EC bulletins, and event countdowns.
- **FR-011**: System MUST support resident suggestion submission and maintenance issue reporting with acknowledgement and tracking reference.
- **FR-012**: System MUST route issue reports to the designated operational team based on issue category.
- **FR-013**: System MUST enforce role-based access control for at least residents, elected body members, and administrators.
- **FR-014**: System MUST support module-level permission toggles per role (for example: notices, bookings, issue routing, and forms) so authorized administrators can grant, change, and revoke role capabilities.
- **FR-015**: System MUST record auditable activity for privileged actions related to announcements, policy updates, role changes, and operational status updates.
- **FR-016**: System MUST allow residents to manage communication preferences and consent for non-emergency updates.
- **FR-017**: System MUST keep core resident workflows usable on both mobile and desktop web views.
- **FR-018**: System MUST provide clear status messaging for submitted requests, bookings, and reports.
- **FR-019**: System MUST authenticate residents using mobile OTP with an optional password fallback path.
- **FR-020**: System MUST require admin approval for new resident account activation so non-residents cannot access resident-only features.
- **FR-021**: System MUST show pending-approval status to newly registered users until an authorized admin approves or rejects access.
- **FR-022**: System MUST verify a new user by matching submitted unit number and registered mobile details against resident records before the account enters admin approval.
- **FR-023**: System MUST reject or hold for review any signup attempt where unit number and mobile details do not match resident records.
- **FR-024**: System MUST manage issue reports using the default lifecycle New -> Acknowledged -> In Progress -> Resolved -> Closed and show current status to residents.
- **FR-025**: System MUST retain privileged-action audit logs for 24 months and keep them accessible for authorized governance review.

### Key Entities *(include if feature involves data)*

- **Resident Account**: Community user identity with unit details, contact profile, communication preferences, identity-verification status, and approval status.
- **Role Profile**: Permission grouping for resident, elected body member, and administrator with assigned capabilities.
- **Facility Slot**: Bookable amenity timeslot with availability state, booking owner, and confirmation details.
- **Summer Camp Registration**: Child enrollment record with guardian details, enrollment status, payment status, and schedule access state.
- **Digital Form Request**: Request record linked to form type, applicant, submission state, and review outcome.
- **Notice Item**: Time-bound message item for banners, bulletins, and event countdown metadata.
- **Feedback Submission**: Resident suggestion with category, message, acknowledgement status, and review state.
- **Issue Ticket**: Maintenance incident record with location, severity, routing team, lifecycle status (New, Acknowledged, In Progress, Resolved, Closed), and resolution notes.
- **Audit Entry**: Immutable activity record for privileged user actions and permission changes, retained for 24 months.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 85% of routine resident information queries are resolved through self-service pages without direct office contact within the first 90 days.
- **SC-002**: At least 80% of amenity booking attempts are completed successfully in under 2 minutes from start to confirmation.
- **SC-003**: At least 90% of summer camp registrations are completed fully online without manual intervention.
- **SC-004**: At least 95% of urgent notices published by authorized users are visible to residents within 60 seconds.
- **SC-005**: At least 90% of submitted issues receive route-to-team acknowledgement within 5 minutes.
- **SC-006**: At least 95% of residents can complete each P1 workflow on first attempt in moderated usability checks.
- **SC-007**: 100% of privileged actions are traceable to an authorized account and timestamp in audit records.
- **SC-008**: Resident satisfaction for digital reception services reaches at least 4.2/5 within one operating quarter.
- **SC-009**: 100% of privileged-action audit records remain retrievable for 24 months for authorized reviewers.

## Constitution Alignment Checklist *(mandatory)*

- Resident value for each user story is explicit and testable.
- Personal data usage is minimized and purpose-defined.
- Consent and communication preference handling is specified.
- Notification or message failure handling is defined.
- Accessibility expectations are stated for desktop and mobile use.

## Assumptions

- The website domain and primary resident access channel already exist and can host these workflows.
- Residents, elected body members, and admins already have or can be provisioned authenticated user accounts.
- Payment completion confirmation for summer camp can be captured from an approved payment process in scope for the website.
- Emergency notices may be sent regardless of non-emergency communication preferences.
- Existing governance policy defines who can serve as elected body member and privileged admin.
- Initial launch will prioritize English content; multilingual expansion can be planned later.
