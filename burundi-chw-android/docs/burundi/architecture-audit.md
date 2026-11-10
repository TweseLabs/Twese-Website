# Architecture audit — Burundi CHW-first adaptation of Simple Android

Audit of the supplied `simple-android-master.zip` (imported unmodified in commit `15815a9`), done
before any change. Every row says what **exists**, what was **modified**, what is **new**, and the
**backend dependency**. "Server-blocked" means the feature needs simple-server changes that are
specified in [`server-implementation.md`](server-implementation.md) but **not implemented**, because
the server source was not part of this assignment.

## Verified environment & baseline

| Item | Verified value |
|---|---|
| Room DB | `AppDatabase` version **123** (`app/src/main/java/org/simple/clinic/AppDatabase.kt`), `exportSchema = true`, schemas in `app/schemas/` |
| Java | `.java-version` = 17.0; Gradle toolchain requires a JDK 17 (JDK 21 runs Gradle) |
| Gradle / AGP / Kotlin | Gradle 9.7.1 wrapper, AGP 9.4.0, Kotlin 2.4.20, KSP 2.3.12 |
| SDK | compileSdk 37 / targetSdk 37 / minSdk 26 (root `build.gradle.kts`) — installed `platforms;android-37.0`, `build-tools;37.0.0` |
| NDK | `androidNdkVersion=21.3.6528147` property; not needed for `assembleQaDebug` |
| Flavors | `qa`, `staging`, `sandbox`, `security`, `production` × `debug`/`release` (several combos filtered) |
| Baseline | Pristine source: `./gradlew :app:assembleQaDebug` **BUILD SUCCESSFUL**; sample upstream unit tests (`consent.*`, `overdue.callresult.*`) pass |
| Architecture | Single-activity, fragments + `ScreenKey`/`Router` navigation (`navigation/v2`), Mobius (event/update/effect) with `MobiusViewModel` for Compose screens, Dagger 2 (`TheActivityComponent`, `AppComponent`), RxJava2, Room + SQLCipher-capable open helper |

## Feature inventory

| Requested capability | Existing (path / class) | Modified in this branch | New in this branch | Backend dependency |
|---|---|---|---|---|
| Auth / login | `login/`, `user/UserSession.kt`, OTP + PIN (`enterotp/`, `security/pin/`), `user/User.kt` (status, `Capabilities.canTeleconsult`) | — | — | Unchanged; needs a Burundi server instance |
| Roles & permissions | Only `User.Capabilities.canTeleconsult`; server `User#access_level` is for dashboard users | `EditMedicinesScreen` refuses CHW role; `PatientSummaryScreen`/`HomeScreen` show CHW entry by role | `chw/access/ChwRole`, `ChwAccessPolicy` (permission table), `ChwSession` (role source, encrypted prefs, debug-only demo role) | **Server-blocked**: role must be sent by server and enforced on every endpoint |
| Area / CHW assignment | Facility + facility *sync group* scoping (`facility/`, `AppDatabase.deletePatientsNotInFacilitySyncGroup`); `Patient.registeredFacilityId`/`assignedFacilityId` (clinical meaning) | — (Patient fields **not** repurposed) | `CommunityArea`, `ChwAssignment`, `PatientChwAssignment` entities + DAOs | **Server-blocked**: areas/assignments are server-authoritative, pull-only on device |
| Patient registration / search / QR | `newentry/`, `registration/`, `instantsearch/`, `scanid/` (BP Passport QR), duplicate detection in search | — | CHW home points to existing Patients tab for lookup/registration | Existing patient sync |
| Clinical BP entry | `bp/entry/` (Mobius), `bp/BloodPressureMeasurement.kt`, `bp/sync/` | — | — | Existing |
| Community screening | none (existing `Feature.Screening` is a diabetes-screening flag in medical history, unrelated) | — | `chw/screening/` (`ScreeningEpisode`, `ScreeningProtocol` asset, `ScreeningClassifier`, `ScreeningPlanner`, `entry/NewScreening*` Mobius screen) | Works offline; upload **server-blocked** |
| Medical history / diagnosis | `medicalhistory/` (diagnosis questions, `NewMedicalHistoryScreen`) | — | Screening never writes `MedicalHistory` | Gating diagnosis questions for CHW in the *registration* flow is **pending** (see implementation plan) |
| Medication | `drugs/` (`PrescribedDrug`, `EditMedicinesScreen`) | CHW refused at `EditMedicinesScreen.onViewCreated` | Patient-reported availability lives in `ChwContact`, never in `PrescribedDrug` | Server must reject CHW prescription writes (**server-blocked**) |
| Appointments / overdue | `overdue/Appointment.kt`, `home/overdue/`, `scheduleappointment/`, `overdue/callresult/` | — (not reused for CHW contacts to avoid polluting clinic overdue lists and the appointment sync contract) | `ChwFollowUpTask`, `ChwContact`, `chw/queue/ChwWorkQueue`, `followup/FollowUpStatusClassifier` | CHW task sync **server-blocked** |
| Referrals | none (teleconsultation is a different concept) | — | `chw/referral/Referral` (`ChwReferral` table), `ReferralStateMachine` | Clinic receipt/acknowledgement **server-blocked** |
| Offline sync | `sync/SyncCoordinator`, `ModelSync`, `SynceableRepository`, `SyncModule`, `DataPushResponse` | `SyncModule` registers `ChwSync` **only** when `Feature.BurundiChwServerSync` is on (default off) | `chw/sync/ChwSyncEngine` (id-scoped DONE marking), `ChwSync`, `ChwSyncApi`, versioned payloads | **Server-blocked** |
| Local purge | `AppDatabase.prune/purge`, `deletePatientsNotInFacilitySyncGroup` | `AppDatabase.purge` also purges soft-deleted synced CHW rows; `clearAppData` clears CHW tables | `ChwRepository.purgeOutsideScope` (never deletes PENDING rows) | Trigger on reassignment/revocation needs server signal |
| Encryption / app lock | `storage/` (SQLCipher support, `EncryptionModule`, `EncryptedSharedPreferences`), `applock/` | — | CHW role stored in **encrypted** prefs | — |
| Feature flags | `feature/Feature.kt`, `Features` (Firebase Remote Config) | Added `ChwWorkflows(true)` and `BurundiChwServerSync(false)` — **no remote-config key** so no third-party service can toggle them | — | — |
| Notifications / SMS | Upstream SMS reminders are server-side; local notifications for app update / drug stock | — | None added (no PHI in notifications) | SMS policy pending approval |
| Localization | `res/values-*` (16 locales), `settings/SettingsModule` language list | Added `rn-BI` (Ikirundi) to non-production language list | `res/values/strings_burundi_chw.xml` (English source), empty `res/values-rn-rBI/` | Translation + clinical review is an external process |
| Analytics / crash | `ReleaseClinicApp` → `FirebaseAnalyticsReporter`; Sentry crash sink; `google-services.json` per flavor belong to upstream | — | CHW analytics names carry no values | **Deployment blocker**: replace/remove upstream Firebase & Sentry before any real data (see deployment checklist) |
| Server endpoints | `manifestEndpoint` property → country/deployment manifest | **Default changed** to unresolvable `https://manifest.burundi-chw.invalid/dev/` so debug builds cannot reach Simple's QA servers | — | A Burundi dev server must be supplied via `-PmanifestEndpoint=` |
| Reporting / dashboard | Server-rendered progress tab (`home/report/`, WebView) | — | Indicator definitions in `data-model.md` §Indicators | **Server-blocked** (aggregates must be computed server-side) |

## Confirmed facts vs assumptions

Confirmed by reading code/building: everything in the "Existing" column; DB v123; baseline build;
that upstream `SyncCoordinator.push` marks *all* PENDING rows DONE after a push loop (a record
created mid-push could be marked DONE without being sent) — the CHW engine avoids this.

Assumptions (need confirmation by the programme):
- CHWs log in with the same phone-number + PIN + OTP mechanism as clinic staff.
- A CHW's "current facility" in Simple can act as the supervising clinic until server assignments exist.
- Colline/quartier is the right lowest area unit for catchments.

## Blockers

1. **simple-server source/instance** — not provided. All remote behaviour is specified, not implemented.
2. **Clinical protocol approval** — thresholds, danger signs, intervals are placeholders (`approved=false`).
3. **Kirundi translations** — structure only.
4. **Third-party services** (Firebase/Sentry) still configured with upstream project files.
