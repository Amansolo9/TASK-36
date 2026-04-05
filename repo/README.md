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
| GET | `/api/organizations` | Authenticated | List all organizations |
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
