# FieldOps Fulfillment & Community Platform - Design Document

## 1. Overview

The FieldOps Fulfillment & Community platform (internally named **StoreHub**) unifies on-site staff check-ins, local order delivery/pickup, and post-service trust building for multi-location operations that may run without internet. It is built as a Spring Boot 3.2 + Angular 17 application backed by PostgreSQL 16, deployed via Docker Compose on a local network or single machine with zero external dependencies.

## 2. Architecture

### 2.1 High-Level Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Frontend | Angular 17 (standalone components, Signals) | PWA-enabled via `ngsw-config.json`; lazy-loaded routes |
| Backend | Spring Boot 3.2, Java 17 | Stateless REST API; AOP-driven audit and auth |
| Database | PostgreSQL 16 | Flyway-managed migrations (27 versions) |
| Deployment | Docker Compose | Three containers: `db`, `backend`, `frontend` (nginx) |
| Security | JWT (HMAC-SHA256) + AES-256 field encryption | No external auth providers |

### 2.2 Deployment Topology

```
┌─────────────────────────────────────────────────────┐
│  Docker Host (local network / single machine)       │
│                                                     │
│  ┌──────────┐   ┌──────────────┐   ┌────────────┐  │
│  │ Angular  │──▶│ Spring Boot  │──▶│ PostgreSQL │  │
│  │ (nginx)  │   │   :8080      │   │   :5432    │  │
│  │  :4200   │   └──────────────┘   └────────────┘  │
│  └──────────┘         │                             │
│                       ▼                             │
│              ┌─────────────────┐                    │
│              │ Local Evidence  │                    │
│              │ File Storage    │                    │
│              └─────────────────┘                    │
└─────────────────────────────────────────────────────┘
```

The frontend proxies all `/api/**` requests to the backend. Evidence files (PDF/JPG/PNG, max 10 MB) are stored on the local filesystem under a configurable path.

### 2.3 Offline-First Design

The entire platform runs on a local network or standalone machine. There are no calls to external services, CDNs, cloud APIs, or online map providers. Location is captured as user-entered text or selection from a preloaded site list. The Angular frontend is configured as a Progressive Web App (service worker) to cache assets for resilience against brief network interruptions within the LAN.

## 3. Organization & Role Model

### 3.1 Organization Hierarchy

```
Enterprise
  └── Site (physical location)
        └── Team (functional group within a site)
```

Each entity in the `organizations` table carries an `OrgLevel` enum (`ENTERPRISE`, `SITE`, `TEAM`) and a nullable `parent_id` referencing its parent. Users are assigned to a `site` and optionally a `team`.

### 3.2 Roles & Permissions

| Role | Scope | Capabilities |
|------|-------|-------------|
| `ENTERPRISE_ADMIN` | All sites | Full CRUD, user role management, audit range queries, incentive/delivery-zone config, experiment management, organization creation |
| `SITE_MANAGER` | Own site | Site-level order/check-in oversight, fraud-alert resolution, quarantine review, appeal arbitration, ticket assignment, analytics dashboards |
| `TEAM_LEAD` | Own site | View check-ins and orders for their site, manage team-scoped operations |
| `STAFF` | Own site | Perform check-ins, fulfill orders, verify pickups, handle assigned tickets |
| `CUSTOMER` | Own data | Place orders, create posts, submit ratings, open tickets, manage addresses |

Authorization is enforced at three levels:
1. **Route guards** (Angular `authGuard` + `roleGuard`) prevent UI access.
2. **@PreAuthorize** annotations on controller methods enforce role checks.
3. **@DataScope** AOP aspect filters query results by the user's organization/site membership.

## 4. Authentication & Security

### 4.1 Authentication Flow

1. User submits `username` + `password` to `POST /api/auth/login`.
2. `AuthService` validates credentials against bcrypt-hashed password (strength 12).
3. On success, a JWT is issued containing `sub` (userId), `role`, `siteId`, `authTime`, and `tokenVersion`.
4. Token expires after **30 minutes** of inactivity. The frontend tracks idle time and clears the session.
5. Privileged actions annotated with `@RequiresRecentAuth` require the user's `lastAuthenticatedAt` to be within the last **10 minutes**; otherwise a re-authentication prompt is triggered via `POST /api/users/reauth`.

### 4.2 Account Lockout

- After **5 consecutive failed login attempts**, the account is locked for **15 minutes** (`lockedUntil` field).
- Successful login resets `failedLoginAttempts` to 0.

### 4.3 Bootstrap Admin

On first startup, if `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`, and `BOOTSTRAP_ADMIN_EMAIL` environment variables are set and no user with that username exists, the `AdminBootstrapService` seeds an `ENTERPRISE_ADMIN` account. This is the only path to create the initial admin without an existing admin.

### 4.4 Password Requirements

Minimum 10 characters, at least one number and one symbol (enforced at registration).

### 4.5 Encryption at Rest

Sensitive fields use AES-256 encryption via a JPA `AttributeConverter` (`EncryptedStringConverter`). Encrypted fields include:

- **User**: `address`, `deviceId`
- **Address**: `street`, `city`, `state`, `zipCode`
- **CheckIn**: `deviceFingerprint`
- **AuditLog**: `deviceFingerprint`, `ipAddress`, `beforeState`, `afterState`
- **CreditScore**: `scoreEncrypted`
- **DeviceBinding**: `deviceHash`

The AES key is supplied via the `AES_KEY` environment variable (64 hex characters = 256 bits).

### 4.6 Data Masking

The Angular frontend uses a `DataMaskPipe` to mask sensitive data fields based on the current user's role, minimizing unnecessary exposure on screen.

## 5. Core Features

### 5.1 Staff Check-In

**Flow**: Single-button check-in captures timestamp, location description, and device fingerprint, then shows a confirmation screen with captured evidence.

**Validation Rules**:
- Allowed window: **15 minutes before** to **10 minutes after** scheduled shift start.
- Duplicate blocking: no valid check-in within **60 minutes**.
- Device binding: first device is auto-bound; unrecognized devices trigger a fraud alert.
- Server-side fingerprint augmentation: SHA-256 hash of IP + User-Agent appended to client fingerprint.

**Fraud Detection** (runs on every check-in attempt):
- **Excessive attempts**: 5+ check-ins within 10 minutes → `EXCESSIVE_CHECKIN_ATTEMPTS` alert.
- **Device mismatch**: unrecognized device hash → `DEVICE_MISMATCH` alert.
- **No active shift**: no `ShiftAssignment` for the date → `NO_ACTIVE_SHIFT` alert.
- **Out of window**: outside allowed window → `OUT_OF_WINDOW` alert.

Flagged records are saved with raw evidence. Resolution requires a supervisor note (minimum 10 characters) and `@RequiresRecentAuth`.

**Status Chips**: `VALID`, `EARLY`, `LATE`, `FLAGGED` — displayed in the dashboard UI.

### 5.2 Order Fulfillment

**Fulfillment Modes**:

| Mode | Behavior | Fee Calculation |
|------|----------|----------------|
| `DELIVERY` | Local delivery to customer address | Zone-based: 0-5 mi = $4.99, 5-10 mi = $7.99. Max 10 miles. Admin-configurable per site via `DeliveryZone` table. |
| `COURIER_HANDOFF` | Third-party courier pickup from site | Requires delivery ZIP; fee calculated same as delivery |
| `PICKUP` | Customer collects from site | No delivery fee; 6-digit verification code generated |

**Pickup Verification**:
1. Order creates a random 6-digit code (100000–999999 via `SecureRandom`).
2. Customer presents code at pickup.
3. Staff enters code via `POST /api/orders/{id}/verify-pickup`.
4. Validation: customer cannot self-redeem; code must match; order must not already be verified.
5. Every attempt (success or denial) is recorded in the `PickupRedemptionLog` with outcome and reason.

**Shipping Labels**: `GET /api/orders/{id}/shipping-label` generates a printable PDF via iText for local printer output.

**Address Book**: Users manage saved addresses via `/api/addresses` (CRUD). Addresses support a `label`, `isDefault` flag, and encrypted street/city/state/zip fields.

**Delivery Zone Groups**: In addition to individual delivery zones, administrators can organize zones into **groups** via `/api/delivery-zone-groups`. Each group belongs to a site and contains:
- **ZIP codes**: Each with an associated distance in miles.
- **Distance bands**: Fee tiers defined by `minMiles`, `maxMiles`, and `fee`.

When calculating delivery fees, the `DeliveryZoneGroupService` is tried first (group-based resolution); if no matching group/band is found, the system falls back to legacy single-zone ZIP matching.

### 5.3 Community & Engagement

**Posts & Comments**: Users create posts with title, body, and optional topic. Posts are scoped to the author's site. Comments are ordered chronologically.

**Voting**:
- Upvote/downvote on posts (cannot vote on own posts).
- Toggle behavior: voting the same type removes the vote; switching type flips it.
- Vote counts maintained on the `Post` entity.

**Following**:
- Follow/unfollow other users (`UserFollow` entity).
- Follow/unfollow topics (`TopicFollow` entity).
- Feed endpoint `GET /api/community/posts/following` returns posts from followed users and topics.

**Favorites**: Bookmark posts via `POST /api/community/posts/{postId}/favorite` (toggle).

**Gamification (Points & Levels)**:

| Action | Points |
|--------|--------|
| New post created | +5 |
| Comment created | +2 |
| Upvote received | +1 |
| Downvote received | -1 |
| Post removed | -10 |
| Quarantined (like ring) | -20 |
| Quarantine reversed | +20 |

Point values are configurable via the admin incentive-rules console.

**Community Levels**:

| Level | Threshold | Badge |
|-------|-----------|-------|
| NEWCOMER | 0 | Newcomer |
| CONTRIBUTOR | 100 | Contributor |
| TRUSTED | 300 | Trusted Member |
| CHAMPION | 700 | Community Champion |

**Like-Ring Detection** (scheduled every 10 minutes):
- Threshold: **20+ votes** between the same two accounts in **24 hours**.
- Detected pairs are quarantined; both voter and post author receive -20 point penalties.
- Site managers review quarantined votes and can mark as legitimate (reversing penalties) or confirm fraud.

### 5.4 Ratings & Appeals

**Two-Way Ratings**: After an order reaches a completed status (`DELIVERED`, `PICKED_UP`, `COURIER_DELIVERED`), both customer and staff can rate each other.

**Rating Dimensions**: 1-5 score for each of `timeliness`, `communication`, `accuracy`, plus an overall `stars` rating and optional text comment.

**Appeal Workflow**:
1. Rated user may appeal within **7 days** of rating creation.
2. Appeal enters `PENDING` status, then `IN_ARBITRATION` when a manager reviews.
3. Resolution: `UPHELD` (rating stands) or `OVERTURNED` (rating reversed). Appeals past the 7-day window are marked `EXPIRED`.
4. Arbitration reviewer, timestamps, and notes are recorded.

**Credit Score Impact**:
- Base score: 500 (range 0–1000).
- Formula: `500 + ratingImpact + communityImpact + orderImpact + disputeImpact`.
- `ratingImpact` = (avgStars - 3) x 40.
- `communityImpact` = communityPoints / 10.
- `orderImpact` = min(completedOrders x 5, 100).
- `disputeImpact` = -disputes x 20.
- Explanation string details the breakdown for user transparency.

### 5.5 After-Sales Support Tickets

**Ticket Types**: `REFUND_ONLY`, `RETURN_AND_REFUND`.

**Status Lifecycle**: `OPEN` → `AWAITING_EVIDENCE` → `UNDER_REVIEW` → `APPROVED`/`REJECTED` → `REFUNDED` → `CLOSED` (or `ESCALATED`).

**Auto-Approve Rule**: Refund amount < $25.00 AND no prior approved tickets for the customer → auto-approved immediately.

**Return Eligibility**: Return requests must be filed within **14 days** of order creation.

**SLA Timer**: First response due within **8 business hours** (Mon-Fri, 9 AM – 5 PM). Hours outside business hours roll to the next business day at 9 AM. `slaBreached` flag set if missed.

**Evidence Upload**: Customers/staff attach proof files (PDF/JPG/PNG, max 10 MB). Files are stored locally with SHA-256 hash for tamper detection. Integrity verification available via `GET /api/tickets/evidence/{id}/verify`.

**Retention**: Ticket data retained for **24 months** (`retentionExpiresAt` = createdAt + 730 days). A scheduled `RetentionCleanupService` purges expired records.

### 5.6 Analytics & Experiments

**Event Tracking**: Standardized events (`PAGE_VIEW`, `CLICK`, `CONVERSION`, `SATISFACTION_RATING`, `CHECKIN`, `ORDER_PLACED`, `TICKET_CREATED`) logged via `POST /api/analytics/events`.

**Site Metrics** (manager/admin only): Computed on-demand for a date range per site:
- **Funnel**: views → clicks → conversions, CTR, conversion rate.
- **Satisfaction**: total ratings, unique raters, coverage percentage.
- **Diversity**: distinct event types, active users, engagement diversity index.

**Retention Cohorts**: Day-1, Day-7, Day-30 retention rates computed from event data.

**A/B & Bandit Experiments**:
- Types: `AB_TEST` (fixed split) and `BANDIT` (adaptive allocation).
- **Site-scoping**: Experiments can be scoped to a specific site (`siteId` field) or run globally (null `siteId`). `SITE_MANAGER` can only create experiments for their own site; `ENTERPRISE_ADMIN` can scope globally or to any site.
- Deterministic bucketing by `hash(userId + experimentName) % variantCount`.
- Version tracking: experiments track a `version` field incremented on updates.
- Feature backtracking: `POST /api/analytics/experiments/{id}/rollback` deactivates and rolls back.
- Outcomes recorded per user per variant.

## 6. Cross-Cutting Concerns

### 6.1 Audit Trail

Every write action is captured via the `@Audited` annotation processed by `AuditAspect` (AOP). The audit log records:
- `userId`, `username`, `action`, `entityType`, `entityId`
- `beforeState` and `afterState` (encrypted JSON snapshots)
- `deviceFingerprint` and `ipAddress` (encrypted)
- `createdAt` (immutable; the `audit_log` table is insert-only with `updatable = false`)

Audit logs are queryable by entity, user, or date range (admin/manager restricted). All audit query endpoints require `@RequiresRecentAuth`. Entity-level queries enforce site scope — the aspect validates that the requesting user has access to the entity's site. For unknown entity types, only `ENTERPRISE_ADMIN` may query.

### 6.2 Rate Limiting & Idempotency

The Spring Security filter chain includes two protective filters before JWT authentication:
- **IdempotencyFilter**: Deduplicates requests to prevent accidental double-submission.
- **RateLimitFilter**: Protects API endpoints from excessive requests.

### 6.3 Multi-Tenancy / Data Scoping

The `@DataScope` annotation and `DataScopeAspect` provide **multi-dimensional** data-scope filtering based on the authenticated user's context. Three scope dimensions are supported:

1. **Site scope** (always enforced): `ENTERPRISE_ADMIN` sees all sites; `SITE_MANAGER`/`TEAM_LEAD` see their site and organizational children; `STAFF`/`CUSTOMER` see their own site only.
2. **Device scope** (opt-in via `requireDevice = true`): Requires the `X-Device-Fingerprint` header. Operations without device context are denied.
3. **Work-order scope** (opt-in via `requireWorkOrder = true`): Requires the `X-Work-Order-Id` header or `workOrderId` query parameter. Operations without work-order context are denied.

The aspect populates a thread-local `DataScopeContext` with `visibleSiteIds`, `teamId`, `deviceHash`, and `workOrderId`. When a required dimension is absent, the aspect **denies by default** with an `AccessDeniedException`. Management roles (`ENTERPRISE_ADMIN`, `SITE_MANAGER`) are exempt from device and work-order enforcement — they perform supervisory queries that should not be gated on those dimensions.

### 6.4 Error Handling

A `GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to structured JSON error responses:

| Exception | HTTP Status | Extra Fields |
|-----------|-------------|--------------|
| `MethodArgumentNotValidException` | `400` | `details` (field-level errors) |
| `IllegalArgumentException` | `400` | — |
| `BadCredentialsException` | `401` | — |
| `AccessDeniedException` | `403` | — |
| `RecentAuthRequiredException` | `403` | `code: "RECENT_AUTH_REQUIRED"` |
| `NotFoundException` | `404` | — |
| `ConflictException` | `409` | — |
| `BusinessRuleException` | `422` | — |
| `ResponseStatusException` | (from exception) | — |
| Generic `Exception` | `500` | `traceId` (8-char UUID prefix for log correlation) |

All error responses share a common shape: `{ timestamp, status, error }`.

### 6.5 Token Revocation

Each user has a `tokenVersion` field. Incrementing it invalidates all previously issued JWTs. The `JwtAuthenticationFilter` compares the token's `tokenVersion` claim against the stored value.

## 7. Database Schema

The database schema is managed by **27 Flyway migrations** (V1–V27). Key tables:

| Table | Purpose |
|-------|---------|
| `users` | Accounts with encrypted PII, lockout fields, token version |
| `organizations` | Hierarchical enterprise → site → team |
| `orders` | Fulfillment with mode, verification code, courier notes |
| `order_items` | Line items per order |
| `check_ins` | Attendance records with encrypted device fingerprint |
| `shift_assignments` | Staff shift schedules per site |
| `ratings` | Multi-dimensional reviews with appeal/arbitration fields |
| `posts`, `comments`, `votes` | Community content |
| `quarantined_votes` | Like-ring detection results |
| `favorites`, `user_follows`, `topic_follows` | Engagement features |
| `point_ledger` | Gamification point transactions |
| `incentive_rules` | Configurable point values per action |
| `support_tickets` | After-sales tickets with SLA and retention |
| `evidence_files` | Ticket attachments with SHA-256 hash |
| `delivery_zones` | ZIP/distance-based delivery fee configuration (legacy) |
| `delivery_zone_groups` | Hierarchical zone groups per site |
| `delivery_zone_zips` | ZIP codes within a zone group |
| `delivery_distance_bands` | Distance-based fee tiers within a zone group |
| `addresses` | User address book (encrypted) |
| `credit_scores` | Trust scores (encrypted) with impact breakdown |
| `device_bindings` | Device-to-user binding (encrypted hash) |
| `fraud_alerts` | Check-in anomaly records |
| `pickup_redemption_log` | Pickup verification audit trail |
| `analytics_events` | Standardized event log |
| `experiments`, `experiment_outcomes` | A/B and bandit test tracking |
| `audit_log` | Immutable action audit trail (encrypted) |

## 8. Frontend Architecture

### 8.1 Component Structure

The Angular 17 frontend uses **standalone components** with **Signals** for reactive state management. All routes are lazy-loaded for code splitting:

| Route | Component | Guard |
|-------|-----------|-------|
| `/dashboard` | DashboardComponent | authGuard |
| `/checkin` | CheckInComponent | authGuard + roleGuard (admin, manager, lead, staff) |
| `/orders` | OrdersComponent | authGuard |
| `/ratings` | RatingsComponent | authGuard |
| `/community` | CommunityFeedComponent | authGuard |
| `/community/dashboard` | CommunityDashboardComponent | authGuard |
| `/tickets` | TicketsComponent | authGuard |
| `/analytics` | SitePerformanceComponent | authGuard + roleGuard (admin, manager) |
| `/admin` | AdminComponent | authGuard + roleGuard (admin, manager) |

### 8.2 Core Services

- **AuthService**: JWT storage, idle timeout tracking (30 min), session state via Signals, re-authentication flow.
- **AuthInterceptor**: Injects `Authorization: Bearer <token>` header on all outgoing API requests.

### 8.3 PWA Support

The application is configured as a Progressive Web App with Angular's service worker (`ngsw-config.json`), enabling asset caching for offline resilience within the local network.

## 9. Configuration

All sensitive configuration is externalized via environment variables:

| Variable | Purpose |
|----------|---------|
| `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Base64-encoded 256-bit HMAC key |
| `AES_KEY` | 64 hex characters for AES-256 encryption |
| `BOOTSTRAP_ADMIN_USERNAME/PASSWORD/EMAIL` | Initial admin seeding |
| `EVIDENCE_STORAGE_PATH` | Local path for evidence file storage |
| `CORS_ORIGINS` | Allowed CORS origins (default: `http://localhost:4200`) |
