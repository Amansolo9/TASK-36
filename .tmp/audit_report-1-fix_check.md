# Follow-up Check: Previously Reported Issues Status

Scope: This is a focused follow-up on issues from the previous inspection report (`.tmp/static_audit_report_rerun4.md`).
Method: Static-only verification, no runtime execution, no code changes.

## Summary
- Fixed: 3
- Partially Fixed: 1
- Not Fixed: 0

## Issue-by-Issue Status

1) Idempotency filter replayed failed statuses and blocked safe retries  
Status: **Fixed**
- What was checked:
  - Non-success responses should release key, not poison replay.
  - Only successful statuses should be committed/replayed.
- Evidence:
  - `backend/src/main/java/com/eaglepoint/storehub/config/IdempotencyFilter.java:25`
  - `backend/src/main/java/com/eaglepoint/storehub/config/IdempotencyFilter.java:38`
  - `backend/src/main/java/com/eaglepoint/storehub/config/IdempotencyFilter.java:124`
  - `backend/src/main/java/com/eaglepoint/storehub/config/IdempotencyFilter.java:133`
  - `backend/src/main/resources/db/migration/V31__idempotency_state_machine.sql:1`

2) Authorization tests were helper-level, not real HTTP endpoint tests  
Status: **Fixed**
- What was checked:
  - Presence of HTTP-layer tests using Spring MVC test stack.
  - Assertions on real routes for 401/403 matrix behavior.
- Evidence:
  - `backend/src/test/java/com/eaglepoint/storehub/controller/MockMvcAuthorizationTest.java:15`
  - `backend/src/test/java/com/eaglepoint/storehub/controller/MockMvcAuthorizationTest.java:30`
  - `backend/src/test/java/com/eaglepoint/storehub/controller/MockMvcAuthorizationTest.java:68`
  - `backend/src/test/java/com/eaglepoint/storehub/controller/MockMvcAuthorizationTest.java:148`
  - `backend/src/test/java/com/eaglepoint/storehub/controller/MockMvcAuthorizationTest.java:219`

3) Multi-dimensional data-scope enforcement remained partial  
Status: **Fixed (by static evidence**
- Evidence
- Required device scope is now explicitly enforced on check-in scoped read:
  - `backend/src/main/java/com/eaglepoint/storehub/service/CheckInService.java:195`
- Required work-order scope is now explicitly enforced on both site-order read methods:
  - `backend/src/main/java/com/eaglepoint/storehub/service/OrderService.java:204`
  - `backend/src/main/java/com/eaglepoint/storehub/service/OrderService.java:240`
- Paged site-order path now applies work-order filtering logic:
  - `backend/src/main/java/com/eaglepoint/storehub/service/OrderService.java:266`
  - `backend/src/main/java/com/eaglepoint/storehub/service/OrderService.java:269`
- Aspect deny-by-default enforcement is present for required dimensions (with explicit management-role exemption):
  - `backend/src/main/java/com/eaglepoint/storehub/aspect/DataScopeAspect.java:113`
  - `backend/src/main/java/com/eaglepoint/storehub/aspect/DataScopeAspect.java:118`

4) Route-level authorization was uneven on sensitive endpoints  
Status: **Fixed**
- What was checked:
  - Explicit method-level `@PreAuthorize("isAuthenticated()")` added to formerly unannotated sensitive authenticated endpoints.
- Evidence:
  - `backend/src/main/java/com/eaglepoint/storehub/controller/OrderController.java:32`
  - `backend/src/main/java/com/eaglepoint/storehub/controller/OrderController.java:40`
  - `backend/src/main/java/com/eaglepoint/storehub/controller/SupportTicketController.java:32`
  - `backend/src/main/java/com/eaglepoint/storehub/controller/SupportTicketController.java:40`
  - `backend/src/main/java/com/eaglepoint/storehub/controller/RatingController.java:27`
  - `backend/src/main/java/com/eaglepoint/storehub/controller/CommunityController.java:39`

## Final Follow-up Conclusion
Most previously reported issues are fixed. One issue (multi-dimensional scope enforcement) is improved but not fully closed yet based on current static evidence.
