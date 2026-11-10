# CHANGELOG — Burundi CHW adaptation

## Unreleased (branch `claude/epic-mendel-kip1o7`)

Not production ready. Synthetic data only. Server features are specified but not implemented.

### Added
- CHW module (`app/src/main/java/org/simple/clinic/chw/`): roles/permissions, configurable
  screening protocol (unapproved draft), triage classifier, screening/follow-up planners, referral
  state machine, work queue, follow-up status classifier, sync engine + versioned payloads.
- Room schema **v124** (`Migration_124`, additive): `CommunityArea`, `ChwAssignment`,
  `PatientChwAssignment`, `ScreeningEpisode`, `ChwReferral`, `ChwFollowUpTask`, `ChwContact`.
- Screens: Community tab (`ChwHomeScreen`), CHW patient view (`ChwPatientScreen`), New community
  screening (`NewScreeningScreen`, Mobius), Follow-up contact (`ChwFollowUpScreen`); CHW card on the
  patient summary.
- Feature flags `ChwWorkflows` (on) and `BurundiChwServerSync` (off), both local-only.
- English string resources for all CHW UI; empty Kirundi (`rn-BI`) resource set and review sheet;
  "Ikirundi (translation pending)" in non-production language list.
- Debug-only demo role and synthetic demo area seeding (ignored on release builds).
- Docs: architecture audit, data model, API contract, server implementation request, clinical rules
  to approve, deployment checklist, implementation plan, translations workflow.
- Tests: JVM unit tests for classifier, planners, access policy, referral states, work queue,
  follow-up status, sync engine (idempotency, mid-push records, invalid records, pull conflicts),
  payload JSON round-trips, protocol asset, screening Update; `Migration124AndroidTest` (device).

### Changed
- `EditMedicinesScreen`: refuses to open for the CHW role.
- `HomeScreen`/`HomeTab`: optional Community tab (upstream tab ordinals unchanged).
- `PatientSummaryScreen`: CHW entry card for CHW/coordinator roles.
- `SyncModule`: registers `ChwSync` only when `BurundiChwServerSync` is enabled.
- `AppDatabase`: new entities/DAOs; CHW tables cleared in `clearAppData` and soft-deleted synced
  rows purged in `purge`.
- `gradle.properties`: default `manifestEndpoint` is now an unresolvable `.invalid` host so builds
  never reach Simple's QA servers by accident.

### Not changed (deliberately)
- `Patient.registeredFacilityId` / `assignedFacilityId`, clinical BP, medical history, prescriptions,
  appointments and all existing sync payloads.
