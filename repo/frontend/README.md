# StoreHub Frontend

Angular 17 standalone-components frontend for the StoreHub FieldOps platform.

## Quick Start

```bash
npm install
npx ng serve --proxy-config proxy.conf.json
```

Open `http://localhost:4200`. The proxy routes `/api` to the backend at `localhost:8080`.

## Run Tests

```bash
npx ng test
```

## Build for Production

```bash
npx ng build --configuration production
```

Output is in `dist/frontend/browser/`, served by nginx in Docker.

## Role-Based Screens

| Route | Roles | Description |
|---|---|---|
| `/dashboard` | All authenticated | Order status chips, check-in status (On Time/Late/Flagged) |
| `/checkin` | STAFF, TEAM_LEAD, SITE_MANAGER, ENTERPRISE_ADMIN | One-tap shift check-in with evidence confirmation |
| `/orders` | All authenticated | Place orders, view history, pickup verification |
| `/ratings` | All authenticated | Submit/view ratings, appeal workflow |
| `/community` | All authenticated | Posts, comments, votes, followed authors feed |
| `/community/dashboard` | All authenticated | Points meter, badges, credit score impact |
| `/tickets` | All authenticated | Support tickets, evidence upload |
| `/analytics` | SITE_MANAGER, ENTERPRISE_ADMIN | Site performance, funnel, diversity, experiments |

## Key API Assumptions

- Backend returns Spring `Page<T>` for list endpoints (`/api/orders/my`, `/api/orders/site/{id}`)
- Frontend unwraps `.content` from page responses
- Auth uses JWT Bearer tokens stored in localStorage
- 30-minute idle timeout with sliding token refresh via `X-Refreshed-Token` header
- Shipping labels are served as PDF (`application/pdf`)

## Project Structure

```
src/app/
  auth/           Login, registration
  dashboard/      Role-based home with status chips
  checkin/        One-tap check-in with evidence display
  orders/         Order CRUD, fulfillment modes, pickup verification
  ratings/        Rating submission, multi-dimensional scores, appeals
  community/      Feed, comments, votes, favorites, followed authors
  tickets/        Support tickets, evidence upload
  analytics/      Site performance, experiments, diversity metrics
  core/
    guards/       Auth + role guards
    interceptors/ JWT interceptor with sliding refresh
    models/       Typed DTOs (fulfillment, community, analytics, tickets)
    pipes/        Data masking pipe
    services/     API services
```
