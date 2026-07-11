# HCM_50 Mobile HR — Analysis & Milestone Plan

Analyzer + gap-checker both PASS (2026-07-10). Target = the **Flutter app at `mobile/`** (NOT backend/web). Milestones **M495–M512**. payroll_impact: false — payslip is read-only display; nothing bypasses backend security (salary masking, hierarchy, tenant isolation all server-side). Verify with `flutter analyze` (+ `flutter test`), NOT mvn boot. App ~35% covered: login/home/profile/leave/payslip/approvals/timesheet/training/requests/goals/team screens exist; auth (Keycloak OIDC + device-local biometric), FCM push, secure token storage all present.

## New backend needed (minor — 4 items)
- **M495 (L)** POST /api/self/attendance/punch {type CLOCK_IN/OUT/BREAK_*, timestamp, latitude, longitude, gpsAccuracy, deviceId, selfieAttachmentId?, offlineQueueId?} → creates raw attendance event scoped to currentEmployee, geofence-validates if policy requires, returns confirmation + geofence status. Extend AttendanceEventRequest with the GPS fields; wrap existing ingest with self-context. Migration only if new columns/settings needed (V305). Offline punch marked source=MOBILE_OFFLINE, outside-geofence → flag + manager approval (default; tenant-configurable).
- **M497 (S)** GET /api/self/attendance/geofences → employee's allowed clock-in locations (org_unit/work_location gps lat/lng + radius, default 100m / accuracy 50m from tenant_setting). 
- **M504 (M, backend part)** GET /api/self/directory → PUBLIC fields only (name, dept, position, work email/phone, photo) — NEVER salary/passport/bank/medical; tenant-scoped search.
- **M512** verify /api/workflow bulk-act exists (it does — M435); no new backend, just wire it.

## Flutter milestones (screens + SelfApi methods over EXISTING endpoints)
### Phase A — Attendance (M496, M498) — consume M495/M497
- **M496 (M)** Attendance screen: geolocator GPS capture, clock-in/out + break buttons, today-status card (from /attendance/summaries), punch history, distance-to-geofence warning. GPS = personal data → consent notice (PRD S26).
- **M498 (S)** Attendance corrections screen: wire existing /api/self/attendance/corrections + /overtime-requests (list + submit).
### Phase B — missing self-service screens (existing endpoints)
- **M499 (S)** Announcements (GET /api/self/announcements, M430) — list, priority badges, read.
- **M500 (S)** Policy acknowledgement (GET/POST /api/self/policies, M138) — list, view, acknowledge.
- **M501 (M)** HR service requests (GET/POST /api/self/hr-requests, M429) — categories, submit, my tickets + status.
- **M502 (M)** Document upload (POST /api/attachments, M16) — camera/gallery picker, type select, upload.
### Phase C — profile & directory
- **M503 (M)** Profile edit (POST /api/self/personal-info/submit) — editable phone/address/emergency contact → change request (approval workflow); bank details NOT editable on mobile.
- **M504 (M, Flutter part)** Directory screen (GET /api/self/directory) — search, public profile, tap-to-call/email.
### Phase D — notifications & settings
- **M505 (M)** Notifications center (GET /api/notifications) — in-app list, read/unread, deep-link on tap; lock-screen push must not expose salary.
- **M506 (S)** Settings — language en/az, biometric toggle, notification prefs, session info, version.
### Phase E — offline & security hardening
- **M507 (M)** Offline read cache (Hive/Drift) — profile, leave balances, announcements, policies; stale indicator; ENCRYPT any cached payslip/salary.
- **M508 (M)** Offline attendance queue — queue punches offline, sync on reconnect with original timestamps (offlineQueueId → M495).
- **M509 (S)** Payslip security — re-auth (biometric/PIN) before view (5-min expiry), screenshot block (Android FLAG_SECURE; iOS best-effort).
### Phase F — enhancements
- **M510 (M)** Team calendar (manager) (GET /api/self/team-calendar, M431) — full calendar, leave/BT/holidays; show "On Leave" not the leave type.
- **M511 (S)** Leave enhancements — half-day toggle, replacement picker, attachment upload, cancel action.
- **M512 (S)** Bulk approvals — multi-select workflow items → /api/workflow bulk-act.

## Confidentiality (app renders masked backend projections; never raw)
Directory = public fields only. Payslip = read-only, re-auth + screenshot-block, encrypt if cached. Team calendar hides leave type. Push content must not leak salary on lock screen. GPS = personal data, consent notice. Cached sensitive data encrypted at rest (flutter_secure_storage / encrypted box).

## Adopted defaults
Geofence radius 100m, GPS accuracy 50m, session timeout 15min idle, payslip re-auth expiry 5min, offline cache 7 days / attendance queue 72h, selfie optional (tenant-configurable), outside-geofence punch → flag + manager approval, root/jailbreak = soft warn, biometric device-local, offline = read-cache + attendance-write-queue only (no multi-entity conflict resolution). Single-country AZ/AZN, en/az localization. Reuse Keycloak OIDC + FCM.

## Build/verify
`cd mobile && flutter pub get && flutter analyze` (must be clean) + `flutter test`. `flutter build apk --debug` for full verification (Firebase config files may be absent — main.dart handles gracefully). Existing screens use FutureBuilder + Card UI + SelfApi methods — new screens follow the same pattern. New api methods go in mobile/lib/api/self_api.dart; routes in the existing nav (home_screen / main.dart).
