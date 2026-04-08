# FieldOps Fulfillment & Community Platform - API Specification

**Base URL**: `http://<host>:8080/api`
**Authentication**: JWT Bearer token in `Authorization` header (except `/api/auth/**`)
**Content-Type**: `application/json` (unless noted)

---

## 1. Authentication

### POST /api/auth/login
Authenticate a user and obtain a JWT token.

**Request Body** (`LoginRequest`):
```json
{
  "username": "string",
  "password": "string"
}
```

**Response** (`AuthResponse`):
```json
{
  "token": "string",
  "type": "Bearer",
  "username": "string",
  "role": "ENTERPRISE_ADMIN | SITE_MANAGER | TEAM_LEAD | STAFF | CUSTOMER",
  "siteId": 1
}
```

**Error Responses**:
- `401` — Invalid credentials
- `423` — Account locked (5 failed attempts, 15 min lockout)

---

### POST /api/auth/register
Register a new user account. All registrations receive the `CUSTOMER` role.

**Request Body** (`RegisterRequest`):
```json
{
  "username": "string",
  "email": "string",
  "password": "string (min 10 chars, 1 number, 1 symbol)",
  "siteId": 1,
  "role": "CUSTOMER"
}
```

**Response** (`AuthResponse`): Same as login.

---

## 2. Users

### GET /api/users
List all users.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

**Response**: `List<UserDto>`

### GET /api/users/me
Get the authenticated user's profile.

**Response** (`UserDto`):
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "role": "STAFF",
  "siteId": 1,
  "siteName": "string",
  "address": "string (masked by role)",
  "deviceId": "string (masked by role)",
  "enabled": true
}
```

### GET /api/users/{id}
Get a user by ID.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

### PATCH /api/users/{id}/role
Update a user's role.
**Auth**: `ENTERPRISE_ADMIN` | **Requires Recent Auth**

**Query Parameters**:
- `role` — Target role enum value

**Response**: `UserDto`

### DELETE /api/users/{id}
Disable a user account.
**Auth**: `ENTERPRISE_ADMIN` | **Requires Recent Auth**

### POST /api/users/reauth
Re-authenticate to satisfy the recent-auth gate (10 min window).

**Request Body** (`ReauthRequest`):
```json
{
  "password": "string"
}
```

**Response**: `AuthResponse` with refreshed token.

---

## 3. Organizations

### POST /api/organizations
Create a new organization node.
**Auth**: `ENTERPRISE_ADMIN` | **Requires Recent Auth**

**Request Body** (`OrganizationDto`):
```json
{
  "name": "string",
  "level": "ENTERPRISE | SITE | TEAM",
  "parentId": 1
}
```

**Response**: `OrganizationDto`

### GET /api/organizations
List all organizations.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF`

**Response**: `List<OrganizationDto>`

### GET /api/organizations/level/{level}
List organizations by level.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF`

**Path Parameters**: `level` — `ENTERPRISE`, `SITE`, or `TEAM`

### GET /api/organizations/{parentId}/children
List child organizations.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF`

---

## 4. Check-Ins

### POST /api/checkins
Record a staff check-in.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF`

**Request Body** (`CheckInRequest`):
```json
{
  "siteId": 1,
  "scheduledTime": "2026-04-05T09:00:00Z",
  "deviceFingerprint": "string",
  "locationDescription": "string"
}
```

**Response** (`CheckInResponse`):
```json
{
  "id": 1,
  "userId": 1,
  "siteId": 1,
  "timestamp": "2026-04-05T08:55:00Z",
  "scheduledTime": "2026-04-05T09:00:00Z",
  "status": "VALID | EARLY | LATE | FLAGGED",
  "message": "Check-in successful",
  "locationDescription": "Main Entrance",
  "deviceEvidenceToken": "string",
  "windowClassification": "ON_TIME | EARLY | LATE",
  "flaggedForReview": false
}
```

**Error Responses**:
- `409` — Duplicate check-in within 60 minutes
- `403` — Fraud alert triggered (saved but flagged)

### GET /api/checkins/site/{siteId}
List check-ins for a site within a date range.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`

**Query Parameters**:
- `start` — ISO 8601 Instant
- `end` — ISO 8601 Instant

### GET /api/checkins/fraud-alerts
List unresolved fraud alerts.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

**Response**: `List<FraudAlert>`

### PATCH /api/checkins/fraud-alerts/{id}/resolve
Resolve a fraud alert with a supervisor note.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `note` — Supervisor note (min 10 characters)

---

## 5. Orders

### POST /api/orders
Create a new order.

**Request Body** (`OrderRequest`):
```json
{
  "siteId": 1,
  "subtotal": 29.99,
  "fulfillmentMode": "PICKUP | DELIVERY | COURIER_HANDOFF",
  "deliveryZip": "90210",
  "courierNotes": "string (optional)"
}
```

**Response** (`OrderResponse`):
```json
{
  "id": 1,
  "customerId": 1,
  "siteId": 1,
  "status": "PENDING",
  "fulfillmentMode": "PICKUP",
  "subtotal": 29.99,
  "deliveryFee": 0.00,
  "total": 29.99,
  "deliveryZip": null,
  "deliveryDistanceMiles": null,
  "pickup": true,
  "pickupVerificationCode": "482910",
  "pickupVerified": false,
  "courierNotes": null,
  "verifiedById": null,
  "verifiedAt": null,
  "createdAt": "2026-04-05T10:00:00Z",
  "updatedAt": "2026-04-05T10:00:00Z"
}
```

### GET /api/orders/{id}
Get an order by ID.

### GET /api/orders/my
List the authenticated user's orders (paginated).

**Query Parameters**: Standard Spring `Pageable` (`page`, `size`, `sort`)

### GET /api/orders/site/{siteId}
List orders for a site (paginated).
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF`

### PATCH /api/orders/{id}/status
Update order status.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF` | **Requires Recent Auth**

**Query Parameters**:
- `status` — Target `OrderStatus` value

**Valid Order Statuses**: `PENDING`, `CONFIRMED`, `PREPARING`, `READY_FOR_PICKUP`, `OUT_FOR_DELIVERY`, `DELIVERED`, `PICKED_UP`, `CANCELLED`, `COURIER_ASSIGNED`, `COURIER_PICKED_UP`, `COURIER_DELIVERED`

### POST /api/orders/{id}/verify-pickup
Verify a pickup order with the 6-digit code.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF`

**Query Parameters**:
- `code` — 6-digit verification code

**Error Responses**:
- `400` — Wrong code, not a pickup order, already verified, or customer self-redemption
- Every attempt logged in `PickupRedemptionLog`

### GET /api/orders/{id}/shipping-label
Download a printable PDF shipping label.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `TEAM_LEAD`, `STAFF`

**Response**: `application/pdf` binary

---

## 6. Ratings

### POST /api/ratings
Submit a rating for a completed order.

**Request Body** (`RatingRequest`):
```json
{
  "orderId": 1,
  "ratedUserId": 2,
  "targetType": "STAFF | CUSTOMER",
  "stars": 4,
  "timelinessScore": 5,
  "communicationScore": 4,
  "accuracyScore": 4,
  "comment": "string (optional)"
}
```

**Response** (`RatingResponse`):
```json
{
  "id": 1,
  "orderId": 1,
  "raterId": 1,
  "ratedUserId": 2,
  "targetType": "STAFF",
  "stars": 4,
  "timelinessScore": 5,
  "communicationScore": 4,
  "accuracyScore": 4,
  "comment": "Great service",
  "appealStatus": null,
  "appealDeadline": "2026-04-12T10:00:00Z",
  "arbitrationReviewerId": null,
  "arbitrationStartedAt": null,
  "arbitrationResolvedAt": null,
  "arbitrationNotes": null,
  "createdAt": "2026-04-05T10:00:00Z"
}
```

**Validation**:
- Order must be in `DELIVERED`, `PICKED_UP`, or `COURIER_DELIVERED` status
- Rater must be a participant in the order
- Cannot rate yourself; cannot duplicate-rate the same order

### GET /api/ratings/user/{userId}
List all ratings for a user.

### GET /api/ratings/user/{userId}/average
Get average star rating for a user.

**Response**:
```json
{
  "userId": 1,
  "averageStars": 4.2
}
```

### POST /api/ratings/{id}/appeal
Submit an appeal against a rating (within 7-day window).

**Query Parameters**:
- `reason` — Appeal reason text

### PATCH /api/ratings/{id}/appeal/resolve
Resolve a pending appeal.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `resolution` — `UPHELD` or `OVERTURNED`
- `notes` — Arbitration notes (optional)

### GET /api/ratings/appeals/pending
List all pending appeals.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

---

## 7. Community

### POST /api/community/posts
Create a new community post.

**Request Body** (`PostRequest`):
```json
{
  "title": "string",
  "body": "string",
  "topic": "string (optional)"
}
```

**Response** (`PostResponse`):
```json
{
  "id": 1,
  "authorId": 1,
  "authorName": "string",
  "title": "string",
  "body": "string",
  "topic": "general",
  "upvotes": 0,
  "downvotes": 0,
  "netVotes": 0,
  "commentCount": 0,
  "currentUserVote": null,
  "createdAt": "2026-04-05T10:00:00Z"
}
```

### GET /api/community/posts
List posts (paginated).

**Query Parameters**: Standard `Pageable`

### GET /api/community/posts/topic/{topic}
List posts by topic (paginated).

### GET /api/community/posts/following
List posts from followed users and topics.

### DELETE /api/community/posts/{id}
Remove a post (soft delete).
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

### POST /api/community/posts/{postId}/comments
Add a comment to a post.

**Request Body** (`CommentRequest`):
```json
{
  "body": "string"
}
```

**Response** (`CommentResponse`):
```json
{
  "id": 1,
  "postId": 1,
  "authorId": 1,
  "authorName": "string",
  "body": "string",
  "createdAt": "2026-04-05T10:00:00Z"
}
```

### GET /api/community/posts/{postId}/comments
List comments for a post.

### POST /api/community/posts/{postId}/vote
Vote on a post (toggle/switch behavior).

**Query Parameters**:
- `type` — `UPVOTE` or `DOWNVOTE`

**Response**: Updated `PostResponse`

### POST /api/community/topics/{topic}/follow
Follow a topic.

### DELETE /api/community/topics/{topic}/follow
Unfollow a topic.

### GET /api/community/topics/following
List followed topics.

**Response**: `List<String>`

### GET /api/community/points/me
Get the authenticated user's gamification profile.

**Response** (`PointsProfile`):
```json
{
  "userId": 1,
  "totalPoints": 245,
  "level": "CONTRIBUTOR",
  "pointsToNextLevel": 55,
  "badge": "Contributor"
}
```

### GET /api/community/points/{userId}
Get another user's gamification profile.

### POST /api/community/posts/{postId}/favorite
Toggle favorite on a post.

**Response**:
```json
{
  "postId": 1,
  "favorited": true
}
```

### GET /api/community/favorites
List favorited post IDs.

**Response**: `List<Long>`

### POST /api/community/users/{userId}/follow
Follow a user.

### DELETE /api/community/users/{userId}/follow
Unfollow a user.

### GET /api/community/following
List followed user IDs.

**Response**: `List<Long>`

### GET /api/community/quarantine/pending
List pending quarantined votes.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

### PATCH /api/community/quarantine/{id}/review
Review a quarantined vote.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `legitimate` — `true` (reverse penalties) or `false` (confirm fraud)

**Response**:
```json
{
  "id": 1,
  "reviewed": true,
  "legitimate": true
}
```

---

## 8. Support Tickets

### POST /api/tickets
Create a support ticket.

**Request Body** (`TicketRequest`):
```json
{
  "orderId": 1,
  "type": "REFUND_ONLY | RETURN_AND_REFUND",
  "description": "string",
  "refundAmount": 19.99
}
```

**Response** (`TicketResponse`):
```json
{
  "id": 1,
  "orderId": 1,
  "customerId": 1,
  "customerName": "string",
  "assignedToId": null,
  "type": "REFUND_ONLY",
  "status": "OPEN",
  "description": "string",
  "refundAmount": 19.99,
  "autoApproved": false,
  "slaBreached": false,
  "firstResponseDueAt": "2026-04-06T01:00:00Z",
  "createdAt": "2026-04-05T10:00:00Z",
  "updatedAt": "2026-04-05T10:00:00Z",
  "evidence": []
}
```

**Auto-Approve**: If `refundAmount < $25.00` and no prior approved tickets → status immediately set to `APPROVED`.

### GET /api/tickets/{id}
Get a ticket by ID.

### GET /api/tickets/my
List the authenticated user's tickets.

### GET /api/tickets/status/{status}
List tickets by status.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `STAFF`

**Valid Statuses**: `OPEN`, `AWAITING_EVIDENCE`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `REFUNDED`, `RETURN_SHIPPED`, `RETURN_RECEIVED`, `CLOSED`, `ESCALATED`

### PATCH /api/tickets/{id}/status
Update ticket status.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`, `STAFF` | **Requires Recent Auth**

**Query Parameters**:
- `status` — Target `TicketStatus`

### PATCH /api/tickets/{id}/assign
Assign a ticket to a staff member.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `staffId` — User ID of the assignee (must be non-CUSTOMER at the same site)

### POST /api/tickets/{id}/evidence
Upload evidence file.

**Request**: `multipart/form-data` with field `file`
- Allowed types: `application/pdf`, `image/jpeg`, `image/png`
- Max size: 10 MB

**Response** (`EvidenceDto`):
```json
{
  "id": 1,
  "fileName": "receipt.pdf",
  "contentType": "application/pdf",
  "fileSize": 245760,
  "sha256Hash": "a1b2c3...",
  "createdAt": "2026-04-05T10:00:00Z"
}
```

### GET /api/tickets/{id}/evidence
List evidence files for a ticket.

**Response**: `List<EvidenceDto>`

### GET /api/tickets/evidence/{evidenceId}/verify
Verify evidence file integrity (re-compute SHA-256 and compare).
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

**Response**:
```json
{
  "evidenceId": 1,
  "integrityVerified": true
}
```

---

## 9. Analytics

### POST /api/analytics/events
Log an analytics event.

**Request Body** (`EventRequest`):
```json
{
  "eventType": "PAGE_VIEW | CLICK | CONVERSION | SATISFACTION_RATING | CHECKIN | ORDER_PLACED | TICKET_CREATED",
  "siteId": 1,
  "target": "string (optional)",
  "metadata": "string (optional JSON)"
}
```

### GET /api/analytics/sites/{siteId}/metrics
Get site performance metrics for a date range.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

**Query Parameters**:
- `start` — ISO 8601 Instant
- `end` — ISO 8601 Instant

**Response** (`SiteMetrics`):
```json
{
  "siteId": 1,
  "eventCounts": { "PAGE_VIEW": 1200, "CLICK": 350, "CONVERSION": 45 },
  "uniqueUsers": { "PAGE_VIEW": 300, "CLICK": 180, "CONVERSION": 40 },
  "funnel": {
    "views": 1200,
    "clicks": 350,
    "conversions": 45,
    "ctr": 0.292,
    "conversionRate": 0.0375
  },
  "satisfaction": {
    "totalRatings": 120,
    "uniqueRaters": 85,
    "totalActiveUsers": 300,
    "satisfactionCoveragePct": 28.33
  },
  "diversity": {
    "distinctEventTypes": 5,
    "distinctActiveUsers": 300,
    "engagementDiversityIndex": 0.85
  },
  "performanceStatus": "string"
}
```

### GET /api/analytics/sites/{siteId}/retention
Get retention cohort data.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

**Query Parameters**:
- `cohortDate` — ISO 8601 Instant

**Response** (`RetentionReport`):
```json
{
  "siteId": 1,
  "cohortDate": "2026-03-01",
  "cohortSize": 150,
  "day1RetentionRate": 0.72,
  "day7RetentionRate": 0.45,
  "day30RetentionRate": 0.28
}
```

### POST /api/analytics/experiments
Create a new experiment.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Request Body** (`ExperimentDto`):
```json
{
  "name": "string (unique)",
  "type": "AB_TEST | BANDIT",
  "variantCount": 2,
  "description": "string (optional)",
  "siteId": 1,
  "active": true
}
```

### GET /api/analytics/experiments
List all experiments.

### GET /api/analytics/experiments/{name}/bucket
Get the deterministic bucket assignment for the authenticated user.

**Response**:
```json
{
  "experiment": "homepage_layout",
  "variant": 1,
  "version": 3
}
```

### POST /api/analytics/experiments/{name}/outcome
Record an experiment outcome.

**Query Parameters**:
- `variant` — Variant index
- `reward` — Numeric reward value

### PUT /api/analytics/experiments/{id}
Update an experiment.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

### PATCH /api/analytics/experiments/{id}/deactivate
Deactivate an experiment.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

### POST /api/analytics/experiments/{id}/rollback
Roll back an experiment (deactivate + version decrement).
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

---

## 10. Audit Trail

### GET /api/audit/entity/{entityType}/{entityId}
Get audit logs for a specific entity.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Response** (`List<AuditLogDto>`):
```json
[
  {
    "id": 1,
    "userId": 1,
    "username": "admin",
    "deviceFingerprint": "string",
    "action": "UPDATE_STATUS",
    "entityType": "Order",
    "entityId": 42,
    "beforeState": "{ ... }",
    "afterState": "{ ... }",
    "ipAddress": "string",
    "createdAt": "2026-04-05T10:00:00Z"
  }
]
```

### GET /api/audit/user/{userId}
Get audit logs for a specific user's actions.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

### GET /api/audit/range
Get audit logs within a date range.
**Auth**: `ENTERPRISE_ADMIN` | **Requires Recent Auth**

**Query Parameters**:
- `start` — ISO 8601 Instant
- `end` — ISO 8601 Instant

---

## 11. Addresses

### POST /api/addresses
Create a new saved address.

**Request Body** (`AddressDto`):
```json
{
  "label": "Home",
  "street": "123 Main St",
  "city": "Springfield",
  "state": "IL",
  "zipCode": "62701",
  "isDefault": true
}
```

**Response**: `AddressDto` with generated `id`.

### GET /api/addresses
List the authenticated user's addresses.

### PUT /api/addresses/{id}
Update an address.

### DELETE /api/addresses/{id}
Delete an address.

---

## 12. Credit Score

### GET /api/credit-score/me
Get the authenticated user's credit score.

**Response** (`CreditScoreDto`):
```json
{
  "userId": 1,
  "score": 580,
  "ratingImpact": 40,
  "communityImpact": 25,
  "orderImpact": 50,
  "disputeImpact": -40,
  "explanation": "Your score of 580 is based on: Rating (+40), Community (+25), Orders (+50), Disputes (-40)"
}
```

### GET /api/credit-score/{userId}
Get a user's credit score.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

---

## 13. Admin: Incentive Rules

### GET /api/admin/incentive-rules
List all incentive point rules.
**Auth**: `ENTERPRISE_ADMIN`

**Response**: `List<IncentiveRule>`
```json
[
  {
    "id": 1,
    "actionKey": "POST_CREATED",
    "points": 5,
    "description": "Points for creating a new post",
    "active": true,
    "updatedAt": "2026-04-01T00:00:00Z"
  }
]
```

### PUT /api/admin/incentive-rules/{actionKey}
Update point value for an action.
**Auth**: `ENTERPRISE_ADMIN` | **Requires Recent Auth**

**Query Parameters**:
- `points` — New point value (integer)

### PATCH /api/admin/incentive-rules/{actionKey}/toggle
Enable or disable an incentive rule.
**Auth**: `ENTERPRISE_ADMIN` | **Requires Recent Auth**

**Query Parameters**:
- `active` — `true` or `false`

---

## 14. Admin: Delivery Zones

### GET /api/delivery-zones/site/{siteId}
List delivery zones for a site.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

### POST /api/delivery-zones
Create a delivery zone.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `siteId` — Site organization ID
- `zipCode` — ZIP code string
- `distanceMiles` — Distance in miles (double)
- `deliveryFee` — Fee amount (BigDecimal)

### PUT /api/delivery-zones/{id}
Update a delivery zone.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `zipCode`, `distanceMiles`, `deliveryFee`, `active`

### DELETE /api/delivery-zones/{id}
Delete a delivery zone.
**Auth**: `ENTERPRISE_ADMIN` | **Requires Recent Auth**

---

## 15. Admin: Delivery Zone Groups

### GET /api/delivery-zone-groups/site/{siteId}
List delivery zone groups for a site.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER`

**Response**: `List<DeliveryZoneGroup>`

### POST /api/delivery-zone-groups
Create a delivery zone group.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `siteId` — Site organization ID
- `name` — Group name

**Response**: `DeliveryZoneGroup`

### POST /api/delivery-zone-groups/{groupId}/zips
Add a ZIP code to a zone group.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `zipCode` — ZIP code string
- `distanceMiles` — Distance in miles (double)

**Response**: `DeliveryZoneGroup`

### DELETE /api/delivery-zone-groups/{groupId}/zips/{zipCode}
Remove a ZIP code from a zone group.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Response**: `DeliveryZoneGroup`

### POST /api/delivery-zone-groups/{groupId}/bands
Add a distance-based fee band to a zone group.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Query Parameters**:
- `minMiles` — Minimum distance (double)
- `maxMiles` — Maximum distance (double)
- `fee` — Delivery fee (BigDecimal)

**Response**: `DeliveryZoneGroup`

### DELETE /api/delivery-zone-groups/{groupId}/bands/{bandId}
Remove a distance band from a zone group.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Response**: `DeliveryZoneGroup`

### PATCH /api/delivery-zone-groups/{groupId}/deactivate
Deactivate a delivery zone group.
**Auth**: `ENTERPRISE_ADMIN`, `SITE_MANAGER` | **Requires Recent Auth**

**Response**: `DeliveryZoneGroup`

---

## Common Response Codes

| Code | Meaning |
|------|---------|
| `200` | Success |
| `201` | Created |
| `400` | Bad request / validation failure |
| `401` | Not authenticated |
| `403` | Forbidden (role or recent-auth check) |
| `404` | Resource not found |
| `409` | Conflict (duplicate) |
| `422` | Business rule violation |
| `423` | Account locked |

## Authentication Notes

- **JWT Expiration**: 30 minutes (1,800,000 ms)
- **Idle Timeout**: 30 minutes (frontend-enforced)
- **Recent Auth Window**: 10 minutes — endpoints marked "Requires Recent Auth" return `403` if `lastAuthenticatedAt` is older than 10 minutes. Use `POST /api/users/reauth` to refresh.
- **Token Revocation**: Incrementing a user's `tokenVersion` invalidates all existing tokens.
