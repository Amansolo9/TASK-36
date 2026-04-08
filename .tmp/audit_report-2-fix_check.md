# Reinspection Report - Previous Findings Status (Static-Only)

Date: 2026-04-08  
Scope: Re-check of findings from `.tmp/static_audit_report.md` only.  
Boundary: Static analysis only (no app start, no Docker, no tests executed).

## Overall Result
- 6 of 6 previously reported issues now appear fixed by static evidence.

## Status by Previous Issue

| Previous Finding | Prior Severity | Current Status | Evidence (Current Code) | Conclusion |
|---|---|---|---|---|
| Staff/team-lead read flows blocked by required device/work-order headers | High | Fixed | `OrderService.getOrdersBySite` uses `@DataScope` without required flags: `backend/src/main/java/com/eaglepoint/storehub/service/OrderService.java:204`, `:240`; `CheckInService.getCheckInsBySite` also plain `@DataScope`: `backend/src/main/java/com/eaglepoint/storehub/service/CheckInService.java:201`; defaults remain optional (`requireDevice=false`, `requireWorkOrder=false`): `backend/src/main/java/com/eaglepoint/storehub/annotation/DataScope.java:30`, `:37`. | Header-gating mismatch is no longer statically present. |
| SITE_MANAGER could mutate global experiments without site isolation | High | Fixed | Site manager creation constrained to own site: `backend/src/main/java/com/eaglepoint/storehub/service/ExperimentService.java:60-67`; mutation requires experiment site ownership: `.../ExperimentService.java:186-191`; experiment tenancy column added: `backend/src/main/resources/db/migration/V32__experiment_site_scope.sql:1-6`; entity mapped with `site_id`: `backend/src/main/java/com/eaglepoint/storehub/entity/Experiment.java:35-39`. | Site isolation controls are present. |
| Author-follow requirement missing in frontend | Medium | Fixed | Author follow API methods exist: `frontend/src/app/core/services/community.service.ts:115-125`; feed UI button and handlers exist: `frontend/src/app/community/community-feed.component.ts:66-70`, `:296-314`. | Author-follow is surfaced in UI and client service. |
| Check-in anomaly threshold mismatch (`>=5` vs `>5`) | Medium | Fixed | Threshold logic now checks strict greater-than: `backend/src/main/java/com/eaglepoint/storehub/service/CheckInService.java:101-103`; comments/state align to "more than 5": `.../CheckInService.java:37`, `:98`. | Prompt semantics aligned. |
| Frontend lacked re-auth flow for recent-auth-required actions | Medium | Fixed | Interceptor handles recent-auth error and retries: `frontend/src/app/core/interceptors/auth.interceptor.ts:34-45`; service and dialog implemented: `frontend/src/app/core/services/reauth.service.ts:33-45`, `:50-58`; dialog mounted at root: `frontend/src/app/app.component.ts:37`; dialog component wired: `frontend/src/app/shared/components/reauth-dialog.component.ts:18-49`. | Re-auth UX flow exists statically. |
| Authorization tests used invalid API payload contracts, reducing trust in coverage | Medium | Fixed | Ticket test payloads now use valid enum value `REFUND_ONLY`: `backend/src/test/java/com/eaglepoint/storehub/controller/MockMvcAuthorizationTest.java:170`, `:412`; enum supports it: `backend/src/main/java/com/eaglepoint/storehub/enums/TicketType.java:4-5`. | Previously invalid payload mismatch is corrected. |

## Manual Verification Required
- Runtime verification of the re-auth UX/retry behavior in browser interactions.
- Runtime verification of experiment scope enforcement using real principals and persisted data.

## Final Reinspection Verdict
- Based on static evidence, the previously reported errors are now fixed.
