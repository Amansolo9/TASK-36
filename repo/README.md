# StoreHub Platform

Multi-location retail operations platform built with Spring Boot 3.2 and Angular 17.

## Architecture

**Backend**

- Spring Boot 3.2 with Java 17
- PostgreSQL 16 with Flyway migrations
- JWT authentication
- AES-256 field-level encryption

**Frontend**

- Angular 17 standalone components with Signals
- nginx reverse proxy

## Prerequisites

Either:

- **Docker** and **Docker Compose** (recommended)

Or all of the following installed locally:

- Java 17
- Maven 3.9+
- Node 20
- PostgreSQL 16

## Quick Start (Docker)

```bash
cp .env.example .env
# Edit .env and set real secrets for DB_PASSWORD, JWT_SECRET, and AES_KEY
docker compose up --build
```

- Frontend: [http://localhost:4200](http://localhost:4200)
- API: [http://localhost:8080](http://localhost:8080)

### First-Time Admin Bootstrap

On a fresh deployment with an empty database, the system needs an initial enterprise admin.

1. Set these environment variables in your `.env`:
   ```
   BOOTSTRAP_ADMIN_USERNAME=admin
   BOOTSTRAP_ADMIN_PASSWORD=Str0ng!P@ssword
   BOOTSTRAP_ADMIN_EMAIL=admin@storehub.local
   ```
2. Start the application — the admin will be created automatically on first startup.
3. Log in with the bootstrap credentials.
4. **Remove the bootstrap variables** from your `.env` after first login.

The bootstrap is a one-time operation: if any ENTERPRISE_ADMIN already exists, it is skipped.

## Quick Start (Manual)

1. Start PostgreSQL and create the `storehub` database:
   ```sql
   CREATE DATABASE storehub;
   ```

2. Set environment variables from `.env.example` (export each var or use your IDE's run configuration).

3. Start the backend:
   ```bash
   cd backend && mvn spring-boot:run
   ```

4. Start the frontend:
   ```bash
   cd frontend && npm install && npx ng serve --proxy-config proxy.conf.json
   ```

## Environment Variables

1. Copy `.env.example` to `.env` and fill in real values.
2. **NEVER** commit `.env` to version control.
3. Generate `JWT_SECRET` with: `openssl rand -base64 32`
4. Generate `AES_KEY` with: `openssl rand -hex 32`

| Variable | Description |
|---|---|
| `DB_HOST` | PostgreSQL hostname (default `db` for Docker, `localhost` for manual) |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Base64-encoded 256-bit key used to sign JWTs |
| `AES_KEY` | 64 hex character string used for AES-256 field encryption |
| `POSTGRES_DB` | Database name (used by the Docker PostgreSQL container) |
| `POSTGRES_USER` | Database user (used by the Docker PostgreSQL container) |
| `POSTGRES_PASSWORD` | Database password (used by the Docker PostgreSQL container) |
| `CORS_ORIGINS` | Comma-separated list of allowed CORS origins |

## Running Tests

**Backend**

```bash
cd backend && mvn test
```

**Frontend**

```bash
cd frontend && npx ng test
```

## Project Structure

```
backend/
  src/main/java/com/eaglepoint/storehub/
    annotation/        # Custom annotations (@RequiresRecentAuth)
    aspect/            # AOP aspects (audit, encryption)
    config/            # Spring configuration
    controller/        # REST controllers
    converter/         # JPA attribute converters
    dto/               # Data transfer objects
    entity/            # JPA entities (incl. DeliveryZone, IncentiveRule, CreditScore)
    enums/             # Enum types (incl. FulfillmentMode)
    repository/        # Spring Data repositories
    security/          # JWT filter, auth entry point, account lockout, token revocation
    service/           # Business logic (incl. FavoriteService, UserFollowService, CreditScoreService)

frontend/
  src/app/
    core/
      services/        # Auth, API, idle-timeout, sliding session refresh services
      guards/          # Route guards
      pipes/           # Custom pipes
      models/          # TypeScript interfaces
    auth/              # Login component
    dashboard/         # Dashboard view
    checkin/           # Check-in management
    orders/            # Order fulfillment (pickup, delivery, shipping)
    ratings/           # Rating & review system with appeals
    community/         # Community features (favorites, user following, gamification)
    tickets/           # Support ticket system with evidence attachments
    analytics/         # Analytics dashboards with experiments and retention
```

## API Endpoints

### Auth

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register a new user |
| POST | `/api/auth/login` | Public | Authenticate and receive JWT |

### Users

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| GET | `/api/users` | ENTERPRISE_ADMIN, SITE_MANAGER | List all users |
| GET | `/api/users/me` | Authenticated | Get current user profile |
| GET | `/api/users/{id}` | ENTERPRISE_ADMIN, SITE_MANAGER | Get user by ID |
| PATCH | `/api/users/{id}/role` | ENTERPRISE_ADMIN (recent auth) | Update user role |
| DELETE | `/api/users/{id}` | ENTERPRISE_ADMIN (recent auth) | Disable user account |
| POST | `/api/users/reauth` | Authenticated | Re-authenticate for sensitive ops |

### Organizations

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/organizations` | ENTERPRISE_ADMIN (recent auth) | Create organization |
| GET | `/api/organizations` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF | List organizations (site-scoped) |
| GET | `/api/organizations/level/{level}` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF | Filter organizations by level |
| GET | `/api/organizations/{parentId}/children` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF | Get child organizations |

### Check-Ins

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/checkins` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF | Create check-in |
| GET | `/api/checkins/site/{siteId}` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD | Get check-ins by site and date range |
| GET | `/api/checkins/fraud-alerts` | ENTERPRISE_ADMIN, SITE_MANAGER | List unresolved fraud alerts |
| PATCH | `/api/checkins/fraud-alerts/{id}/resolve` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Resolve a fraud alert |

### Orders

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/orders` | Authenticated | Create order |
| GET | `/api/orders/{id}` | Authenticated | Get order by ID |
| GET | `/api/orders/my` | Authenticated | List current user's orders (paginated) |
| GET | `/api/orders/site/{siteId}` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF | List orders by site (paginated) |
| PATCH | `/api/orders/{id}/status` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF (recent auth) | Update order status |
| POST | `/api/orders/{id}/verify-pickup` | STAFF+ (site-scoped; customer self-redeem blocked) | Staff-verified pickup handoff |
| GET | `/api/orders/{id}/shipping-label` | ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF | Download shipping label PDF |

### Ratings

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/ratings` | Authenticated | Submit rating |
| GET | `/api/ratings/user/{userId}` | Self or ENTERPRISE_ADMIN, SITE_MANAGER | Get ratings for user |
| GET | `/api/ratings/user/{userId}/average` | Authenticated | Get average rating for user |
| POST | `/api/ratings/{id}/appeal` | Authenticated | Submit appeal for a rating |
| PATCH | `/api/ratings/{id}/appeal/resolve` | ENTERPRISE_ADMIN, SITE_MANAGER | Resolve a rating appeal |
| GET | `/api/ratings/appeals/pending` | ENTERPRISE_ADMIN, SITE_MANAGER | List pending appeals |

### Community

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/community/posts` | Authenticated | Create post |
| GET | `/api/community/posts` | Authenticated | Get paginated feed |
| GET | `/api/community/posts/topic/{topic}` | Authenticated | Get posts by topic |
| GET | `/api/community/posts/following` | Authenticated | Get posts from followed users |
| DELETE | `/api/community/posts/{id}` | ENTERPRISE_ADMIN, SITE_MANAGER | Remove post |
| POST | `/api/community/posts/{postId}/comments` | Authenticated | Add comment to post |
| GET | `/api/community/posts/{postId}/comments` | Authenticated | Get comments for post |
| POST | `/api/community/posts/{postId}/vote` | Authenticated | Vote on post |
| POST | `/api/community/topics/{topic}/follow` | Authenticated | Follow topic |
| DELETE | `/api/community/topics/{topic}/follow` | Authenticated | Unfollow topic |
| GET | `/api/community/topics/following` | Authenticated | List followed topics |
| GET | `/api/community/points/me` | Authenticated | Get own gamification points |
| GET | `/api/community/points/{userId}` | Authenticated | Get user gamification points |
| POST | `/api/community/posts/{postId}/favorite` | Authenticated | Toggle favorite on post |
| GET | `/api/community/favorites` | Authenticated | List favorited post IDs |
| POST | `/api/community/users/{userId}/follow` | Authenticated | Follow user |
| DELETE | `/api/community/users/{userId}/follow` | Authenticated | Unfollow user |
| GET | `/api/community/following` | Authenticated | List followed user IDs |
| GET | `/api/community/quarantine/pending` | ENTERPRISE_ADMIN, SITE_MANAGER | List pending quarantined votes |
| PATCH | `/api/community/quarantine/{id}/review` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Review quarantined vote |

### Support Tickets

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/tickets` | Authenticated | Create support ticket |
| GET | `/api/tickets/{id}` | Authenticated | Get ticket by ID |
| GET | `/api/tickets/my` | Authenticated | List own tickets |
| GET | `/api/tickets/status/{status}` | ENTERPRISE_ADMIN, SITE_MANAGER, STAFF | List tickets by status |
| PATCH | `/api/tickets/{id}/status` | ENTERPRISE_ADMIN, SITE_MANAGER, STAFF (recent auth) | Update ticket status |
| PATCH | `/api/tickets/{id}/assign` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Assign ticket to staff |
| POST | `/api/tickets/{id}/evidence` | Authenticated | Upload evidence file |
| GET | `/api/tickets/{id}/evidence` | Authenticated | List evidence for ticket |
| GET | `/api/tickets/evidence/{evidenceId}/verify` | ENTERPRISE_ADMIN, SITE_MANAGER | Verify evidence file integrity |

### Analytics

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/analytics/events` | Authenticated | Log analytics event |
| GET | `/api/analytics/sites/{siteId}/metrics` | ENTERPRISE_ADMIN, SITE_MANAGER | Get site metrics for date range |
| GET | `/api/analytics/sites/{siteId}/retention` | ENTERPRISE_ADMIN, SITE_MANAGER | Get retention cohort report |
| POST | `/api/analytics/experiments` | ENTERPRISE_ADMIN, SITE_MANAGER | Create experiment |
| GET | `/api/analytics/experiments` | Authenticated | List active experiments |
| GET | `/api/analytics/experiments/{name}/bucket` | Authenticated | Get experiment bucket assignment |
| POST | `/api/analytics/experiments/{name}/outcome` | Authenticated | Record experiment outcome |
| PATCH | `/api/analytics/experiments/{id}/deactivate` | ENTERPRISE_ADMIN, SITE_MANAGER | Deactivate experiment |

### Audit

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| GET | `/api/audit/entity/{entityType}/{entityId}` | ENTERPRISE_ADMIN, SITE_MANAGER | Get audit trail for entity |
| GET | `/api/audit/user/{userId}` | ENTERPRISE_ADMIN, SITE_MANAGER | Get audit trail for user |
| GET | `/api/audit/range` | ENTERPRISE_ADMIN | Get audit trail by date range |

### Addresses

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| POST | `/api/addresses` | Authenticated | Create address |
| GET | `/api/addresses` | Authenticated | List own addresses |
| PUT | `/api/addresses/{id}` | Authenticated | Update address |
| DELETE | `/api/addresses/{id}` | Authenticated | Delete address |

### Credit Score

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| GET | `/api/credit-score/me` | Authenticated | Get own credit score |
| GET | `/api/credit-score/{userId}` | ENTERPRISE_ADMIN, SITE_MANAGER | Get credit score for user |

### Incentive Rules (Admin)

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| GET | `/api/admin/incentive-rules` | ENTERPRISE_ADMIN | List all incentive rules |
| PUT | `/api/admin/incentive-rules/{actionKey}` | ENTERPRISE_ADMIN (recent auth) | Update rule point value |
| PATCH | `/api/admin/incentive-rules/{actionKey}/toggle` | ENTERPRISE_ADMIN (recent auth) | Enable/disable rule |

### Delivery Zones

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| GET | `/api/delivery-zones/site/{siteId}` | ENTERPRISE_ADMIN, SITE_MANAGER | List active zones for site |
| POST | `/api/delivery-zones` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Create delivery zone |
| PUT | `/api/delivery-zones/{id}` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Update delivery zone |
| DELETE | `/api/delivery-zones/{id}` | ENTERPRISE_ADMIN (recent auth) | Delete delivery zone |

### Delivery Zone Groups (ZIP Lists + Distance Bands)

| Method | Path | Auth/Role | Description |
|---|---|---|---|
| GET | `/api/delivery-zone-groups/site/{siteId}` | ENTERPRISE_ADMIN, SITE_MANAGER | List active zone groups |
| POST | `/api/delivery-zone-groups` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Create zone group |
| POST | `/api/delivery-zone-groups/{id}/zips?zipCode=X&distanceMiles=Y` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Add ZIP with distance |
| DELETE | `/api/delivery-zone-groups/{id}/zips/{zip}` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Remove ZIP |
| POST | `/api/delivery-zone-groups/{id}/bands?minMiles=X&maxMiles=Y&fee=Z` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Add distance band |
| DELETE | `/api/delivery-zone-groups/{id}/bands/{bandId}` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Remove band |
| PATCH | `/api/delivery-zone-groups/{id}/deactivate` | ENTERPRISE_ADMIN, SITE_MANAGER (recent auth) | Deactivate group |

**Configuration sequence**: Create group -> Add ZIP codes with distances -> Add distance bands (e.g., 0-5mi=$4.99, 5-10mi=$7.99). Order pricing matches ZIP to group, then selects fee from the band covering that ZIP's distance.

## Key Features by Phase

**Phase 1 -- Core Platform**
- User registration and JWT authentication
- Organization and location hierarchy management
- Role-based access control (ENTERPRISE_ADMIN, SITE_MANAGER, TEAM_LEAD, STAFF)
- Account lockout after repeated failed login attempts

**Phase 2 -- Check-Ins & Fulfillment**
- Employee check-in / check-out with location tracking
- Order creation and status tracking
- Fulfillment modes: pickup (with verification code), delivery, and shipping
- Shipping label PDF generation
- Delivery zone administration per site

**Phase 3 -- Ratings & Community**
- Multi-dimensional rating system with appeals workflow
- Community discussion posts with topic following
- Post voting with vote-ring quarantine detection
- Gamification points and leaderboard profiles
- Favorites (bookmark posts)
- User following and followed-user feed

**Phase 4 -- Support & Analytics**
- Support ticket lifecycle management with evidence attachments and integrity verification
- Analytics event logging, site metrics, and retention cohort reports
- A/B experiment framework (create, bucket, record outcomes, deactivate)

**Phase 5 -- Addresses & Fraud Resolution**
- Address book management
- Fraud alert detection and resolution workflows

**Phase 6 -- Credit Score & Incentives**
- Credit score tracking per user (encrypted at rest)
- Configurable incentive rules for gamification point awards (admin)

**Cross-Cutting Concerns**
- Audit trail via AOP aspect logging (per-entity, per-user, date-range queries)
- AES-256 encryption of sensitive fields (credit score, audit columns)
- Role-based access control (RBAC) on every endpoint
- Token revocation via token-version column
- Sliding session refresh for active users
- Idle timeout with automatic session logout
- Re-authentication gate for sensitive operations (@RequiresRecentAuth)

## Database Migrations

| Migration | Description |
|---|---|
| V1 | Initial schema -- users, organizations, locations, roles |
| V2 | Fulfillment schema -- orders, order items, check-ins |
| V3 | Community schema -- posts, comments, reactions |
| V4 | Support tickets schema -- tickets, ticket comments |
| V5 | Analytics schema -- summary tables, trend snapshots |
| V6 | Audit log schema -- audit_log table |
| V7 | Rating dimensions -- multi-dimensional rating support |
| V8 | Address book and fraud resolution tables |
| V9 | Favorites and user follows |
| V10 | Credit score and incentive configuration |
| V11 | Account lockout |
| V12 | Token version (token revocation support) |
| V13 | Experiment outcomes |
| V14 | Check-in location |
| V15 | Widen audit encrypted columns |
| V16 | Fulfillment mode and delivery zone admin |
| V17 | Encrypt credit score |
| V18-V27 | Audit immutability, courier handoff, experiment versioning, shift assignments, pickup verifier, order staff, credit score fix, pickup redemption log, arbitration workflow, community site scope |
| V28 | Delivery zone groups with ZIP lists and distance bands |
| V29 | ZIP-to-distance mapping |
| V30 | Idempotency keys table |
| V31 | Idempotency state machine (state + response_body columns) |
| V32 | Experiment site-scope (site_id column for tenant isolation) |

## Remediation Notes

### Required Environment Variables

Docker Compose requires a `.env` file. Copy from `.env.example`:
```bash
cp .env.example .env
# Edit .env and set real values for ALL required variables
```

Required: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `AES_KEY`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.
Optional: `BOOTSTRAP_ADMIN_USERNAME/PASSWORD/EMAIL` (for first-admin provisioning).

### Delivery Zone Configuration

Delivery pricing supports ZIP code groups and distance bands:
- Create a zone group per site with a list of ZIP codes
- Define distance bands (e.g., 0-5mi=$4.99, 5-10mi=$7.99) per group
- Fee is selected by matching the delivery ZIP to a group, then the distance to a band
- Legacy single-ZIP zones remain as fallback

### Rating Dimensional Scores

All three dimensional scores (timeliness, communication, accuracy) are required (1-5) when submitting a rating. Missing values are rejected by validation.

### Data Scope Enforcement

Access control enforces three dimensions:
- **Site scope**: All resources are site-bound; non-admin users can only access their own site
- **Device scope**: Check-in operations verify the device is bound to the user (via `DeviceBinding` entity); unbound devices are flagged
- **Work-order scope**: Order status updates enforce that STAFF/TEAM_LEAD must be the assigned staff for that order; managers/admins can override

Deny-by-default: null site returns `false` for non-admins. Device mismatch throws `AccessDeniedException`. Missing shift assignment flags the check-in.

### Offline-First Write Queue

Critical write operations route through an IndexedDB-backed offline queue:
- **Check-in**, **order creation/status**, **ticket creation**, **community posts/votes/comments**, **ratings**
- When online: executes immediately with `X-Idempotency-Key` header
- When offline: queues locally, replays when connectivity returns
- Status shown in UI: synced / queued offline / retrying / failed

### Idempotency Semantics and Retry Policy

The backend `IdempotencyFilter` implements a state-machine with safe retry semantics:

**State machine:**
- `IN_PROGRESS` — request claimed and executing; concurrent duplicates receive `409 Conflict`
- `SUCCEEDED` — 2xx response committed; future duplicates receive the cached response (replay)

**Key rules:**
- Only successful responses (2xx: 200, 201, 202, 204) are committed and cached for replay
- Non-terminal failures (401, 403, 409, 5xx) **do not poison the key** — the key is released so the same idempotency key can be retried
- Exceptions during execution release the key for retry
- Keys are SHA-256 hashed before storage

**Offline replay behavior:**
- When a queued offline request is replayed after a token expiry (401), the key is released
- The frontend can re-authenticate and retry with the same idempotency key
- Only after a successful 2xx response is the key permanently committed
- This prevents the scenario where a transient auth failure permanently blocks a valid operation

**Concurrent handling:**
- The first request with a new key claims it with `IN_PROGRESS` via atomic `INSERT ... ON CONFLICT DO NOTHING`
- Concurrent duplicates see `IN_PROGRESS` and receive `409 Conflict` with `retryable: true`
- After the first request completes, retries will either replay (if succeeded) or execute (if released)

**Migration:** `V31__idempotency_state_machine.sql` adds `state` and `response_body` columns.

### Data Scope Enforcement

Access control enforces multiple dimensions via the `@DataScope` annotation and `DataScopeAspect`:

- **Site scope** (always enforced): All resources are site-bound; non-admin users can only access their own site hierarchy
- **Device scope** (optional on reads): When `X-Device-Fingerprint` header is provided, results are filtered by device. Not required for dashboard/list reads.
- **Work-order scope** (optional on reads): When `X-Work-Order-Id` header is provided, results are filtered by work-order. Not required for dashboard/list reads.
- **Team scope**: Staff with team assignments see only their assigned/unassigned orders

**Deny-by-default behavior (writes and security checks):**
- Null site returns `false` for non-admins
- Device mismatch throws `AccessDeniedException`
- Missing shift assignment flags the check-in as FLAGGED
- Write operations enforce scope via service-layer ownership checks

**Context propagation and consumption:**
- `DataScopeAspect` extracts device hash from `X-Device-Fingerprint` header (optional)
- `DataScopeAspect` extracts work-order ID from `X-Work-Order-Id` header or `workOrderId` param (optional)
- Both values are stored in thread-local `DataScopeContext` for consumption by services
- `CheckInService.getCheckInsBySite` filters check-in results by device hash when present
- `OrderService.getOrdersBySite` filters orders by work-order ID when present
- Community, support ticket, user, and organization reads are site-scoped only

### Route-Level Authorization

Every controller endpoint has an explicit method-level authorization annotation:
- `@PreAuthorize("isAuthenticated()")` — for endpoints open to any authenticated user
- `@PreAuthorize("hasRole('ENTERPRISE_ADMIN')")` — for admin-only endpoints
- `@PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")` — for manager+ endpoints
- Service-layer object/scope checks provide defense-in-depth on top of route-level guards

### Authorization Test Coverage

**MockMvc HTTP integration tests** (`MockMvcAuthorizationTest`) exercise the real Spring Security filter chain:
- Unauthenticated requests → 401 (orders, check-ins, tickets, ratings, audit, admin, community, addresses)
- Wrong role → 403 (CUSTOMER can't check-in, can't update order status, can't access audit, etc.)
- Correct role → success (CUSTOMER creates orders, STAFF checks in, ENTERPRISE_ADMIN manages users)
- Cross-site denial → 403 via service-layer SiteAuthorizationService

**Service-level authorization tests** (`EndpointAuthorizationTest`, `HttpAuthorizationMatrixTest`, `SecurityIntegrationTest`) cover:
- Cross-site access denial (STAFF at Site A cannot access Site B)
- Null-site denial for non-admins
- Device scope mismatch denial
- Work-order scope enforcement
- Admin bypass behavior
- Owner self-access allowance
- Exception handler sanitization (500 responses don't leak internals)

**Data-scope enforcement tests** (`DataScopeEnforcementTest`) prove:
- `requireDevice=true` with no device header → denied (STAFF/TEAM_LEAD)
- `requireWorkOrder=true` with no work-order → denied (STAFF/TEAM_LEAD)
- ENTERPRISE_ADMIN exempt from requireDevice — succeeds without device header
- SITE_MANAGER exempt from requireWorkOrder — succeeds without work-order header
- Correct full-scope (site + device + work-order) → succeeds with all context set
- Same site but wrong device → device hash propagated for service-layer comparison
- Same site + device but wrong work-order → work-order propagated for service-layer comparison
- Dashboard/list reads without device/work-order headers succeed for STAFF and TEAM_LEAD
- Optional device header on reads is propagated for filtering
- Context is cleared after method execution (even on exception)

**Experiment site-scoping tests** (`ExperimentServiceTest`) prove:
- SITE_MANAGER can create/update/deactivate experiments scoped to their own site
- SITE_MANAGER cannot create experiments for another site → AccessDenied
- SITE_MANAGER cannot update/deactivate global or other-site experiments → AccessDenied
- ENTERPRISE_ADMIN can manage any experiment (global or site-scoped)

**Idempotency tests** (`IdempotencyFilterTest`) prove:
- 2xx responses are committed and replayed correctly
- 401/403/5xx do not poison retries
- Retry with same key after transient failure executes business logic again
- Concurrent duplicates get 409
- Exceptions during execution release the key

### Data Scope: When Headers Are Required vs Not Applicable

The `@DataScope` annotation controls multi-dimensional scope enforcement:

| Service Method | requireDevice | requireWorkOrder | Notes |
|---|---|---|---|
| `CheckInService.getCheckInsBySite` | **No** | No | Dashboard/list read — device header is optional (filters results if provided) |
| `OrderService.getOrdersBySite` | No | **No** | Dashboard/list read — work-order header is optional (filters results if provided) |
| `CheckInService.checkIn` | N/A (manual) | N/A | Device fingerprint passed in request body, not header |
| Write endpoints | Varies | Varies | Scope enforced at service layer via object ownership checks |

**Key design decision:** General list/dashboard reads do NOT require `X-Device-Fingerprint` or `X-Work-Order-Id` headers. This prevents frontline staff from seeing 403 errors on dashboard loads. When these headers ARE provided, results are filtered accordingly. Write operations and object-specific reads enforce scope via service-layer ownership checks.

### Experiment Scope and Authorization

Experiments support site-scoped tenancy (V32 migration):

| Role | Can Create | Can Mutate | Scope |
|---|---|---|---|
| ENTERPRISE_ADMIN | Global or site-scoped | Any experiment | Unrestricted |
| SITE_MANAGER | Own-site only | Own-site only | Cannot touch global or other-site experiments |

- `site_id` column on `experiments` table (nullable — NULL = global)
- SITE_MANAGER attempting to create/update/deactivate/rollback a global or other-site experiment gets 403
- Active experiment listing is site-filtered for non-admin users (shows global + own-site)

### Author Follow/Unfollow UX

The community feed displays a **Follow/Following** button next to each post author:
- State is loaded on component init via `GET /api/community/following`
- Clicking toggles follow/unfollow via `POST/DELETE /api/community/users/{userId}/follow`
- Button shows "Following" (filled) when already following, "Follow" (outlined) when not
- Topic following remains fully functional alongside author following

### Recent-Auth Frontend Recovery Flow

When a privileged action (marked with `@RequiresRecentAuth`) is attempted after the auth window expires:

1. Backend returns `403` with `{ "code": "RECENT_AUTH_REQUIRED", "error": "Recent authentication required..." }`
2. Frontend `authInterceptor` detects the `RECENT_AUTH_REQUIRED` code
3. `ReauthService` opens a modal dialog prompting for password re-entry
4. On success: `POST /api/users/reauth` refreshes the JWT token
5. The original failed request is automatically retried with the fresh token
6. On cancel: the original 403 error is propagated to the calling code

**Applies to:** order status mutations, fraud alert resolution, rating appeal resolution, quarantine review, user role changes, delivery zone management, incentive rule changes, and all other `@RequiresRecentAuth` endpoints.

### Check-In Anomaly Threshold

The anomaly detection rule matches the spec: **more than 5 attempts in 10 minutes**.

- `MAX_ALLOWED_CHECKIN_ATTEMPTS = 5`
- Condition: `(previousAttempts + 1) > MAX_ALLOWED_CHECKIN_ATTEMPTS`
- 5 total attempts (4 previous + current) = **not flagged**
- 6 total attempts (5 previous + current) = **flagged**
- Flagged check-ins are persisted with raw evidence for supervisor review

### Test Strategy: Authorization vs Validation

Authorization and validation tests are separated for clarity:

- **`MockMvcAuthorizationTest`**: Proves HTTP-level auth behavior using valid DTO payloads. Tests 401 (unauthed), 403 (wrong role), and 200 (correct role).
- **`PayloadValidationTest`**: Proves DTO validation returns 400 for missing/invalid fields, independently of role. Covers orders, ratings, posts, and tickets.
- **`EndpointAuthorizationTest` / `HttpAuthorizationMatrixTest`**: Service-level authorization without HTTP payloads.

All authorization tests use contract-accurate payloads matching actual DTO field names (e.g., `timelinessScore` not `timeliness`, `body` not `content`).

### Running All Tests

```bash
# Backend tests (includes all authorization, idempotency, scope, and experiment tests)
cd backend && mvn test

# Frontend tests (includes community follow, reauth, and interceptor tests)
cd frontend && npx ng test

# Full test suite (backend + frontend + static checks)
./run_tests.sh
```
