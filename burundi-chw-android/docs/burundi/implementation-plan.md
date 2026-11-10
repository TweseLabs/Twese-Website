# Implementation plan & status — Burundi CHW-first Simple

Repository layout: this Android project lives in `burundi-chw-android/` of the
`TweseLabs/Twese-Website` repository, branch `claude/epic-mendel-kip1o7`. Commit `15815a9` is the
unmodified upstream import; all Burundi work is in later commits so the diff is reviewable.

## Feature status (plain English)

Legend — **E2E**: works end-to-end; **Android**: implemented and tested on Android, works offline,
but nothing leaves the phone; **Server-blocked**: needs the server changes in
`server-implementation.md`; **Postponed**: deliberately not built in V1.

| Feature | Status | Where |
|---|---|---|
| Programme roles & permission table (CHW / clinician / coordinator / admin) | Android (UI gating + early refusal). Real enforcement **server-blocked** | `chw/access/` |
| Role delivered by server | **Server-blocked**; debug builds can set a clearly labelled *demo* role | `ChwSession` |
| CHW home tab: work queue (urgent, overdue, today, this week, awaiting confirmation, later), my-patients count, area/clinic, unsynced count | Android | `chw/home/ChwHomeScreen.kt`, `chw/queue/ChwWorkQueue.kt` |
| Patient lookup / registration / QR BP Passport | E2E via **existing** Simple flows (unchanged) | Patients tab |
| Community screening episode (consent, location, measurement instructions, 2 readings with timing, danger-sign checklist, special populations, triage result + guidance, saved-on-phone state) | Android, offline | `chw/screening/entry/` |
| Configurable, versioned screening rules with "draft/unapproved" labelling and clinician-review fallback | Android | `assets/burundi/screening_protocol.json`, `ScreeningClassifier` |
| Re-screening without duplicating the patient; fulfils open repeat tasks | Android | `ScreeningPlanner` |
| Automatic follow-up task / referral creation by outcome | Android | `ScreeningPlanner` |
| Urgent guidance usable offline (go now; app does not alert clinic) | Android | result view |
| CHW patient view: screening history vs. clinic BP (provenance labelled), referrals with receipt state, next actions | Android | `chw/patient/` |
| Follow-up contact (method, outcome, optional BP, danger signs, patient-reported medicine availability, side-effect concern, next date, short note) | Android | `chw/followup/entry/ChwFollowUpScreen.kt`, `FollowUpPlanner` |
| Distinct missed-clinic / missed-CHW / unreachable / declined / transferred / lost categories | Android (logic + tests); not yet shown in UI | `FollowUpStatusClassifier` |
| CHW cannot edit medication orders | Android (early refusal on `EditMedicinesScreen`); server rejection **server-blocked** | `drugs/selection/EditMedicinesScreen.kt` |
| CHW cannot answer diagnosis questions in registration flow | **Pending** (Android) — the upstream `NewMedicalHistoryScreen` still asks "diagnosed with hypertension?" during registration | next slice |
| Referral state machine (server-only "received", clinician-only accept/complete) | Android logic + tests; clinic side **server-blocked** | `ReferralStateMachine` |
| Clinician acknowledgement / disposition UI | **Server-blocked** (no clinician actions are faked locally) | — |
| Sync of CHW records (versioned payloads, idempotent upsert, id-scoped DONE, no overwrite of pending) | Android client implemented + tested against fakes; **off** until server exists | `chw/sync/` |
| Area / CHW / patient assignments | Android tables + demo seeding in debug builds; server-authoritative sync **server-blocked** | `chw/area/` |
| Local purge of out-of-scope data (never deletes unsynced) | Android method; trigger **server-blocked** | `ChwRepository.purgeOutsideScope` |
| Kirundi | Structure + review sheet; translations **pending** (English fallback) | `values-rn-rBI/`, `docs/burundi/translations/` |
| Supervisor aggregate dashboard / indicators | Definitions only; **server-blocked** | `data-model.md` §Indicators |
| New app name / applicationId / icon | **Postponed** pending branding & Firebase decision (see below) | — |
| SMS reminders | **Postponed** (needs opt-in policy; no health wording) | — |
| Photo readout / OCR / Bluetooth / AI risk / AI prescribing / SMS-only registration | **Postponed by design** | — |
| DHIS2 / EHR export | Off; documentation only | — |

## Pilot demo path (what can be shown today)

`CHW login → Community tab → open a person → New community screening → saved offline → task in
work queue → referral shows "Saved on phone – clinic has NOT received it" → follow-up contact → task
completed`. Login needs *a* server; use a Burundi dev server (or Simple's sandbox **only with
synthetic data and Simple's permission**). Clinician acknowledgement is **not** demonstrable
because the server endpoints do not exist; the UI says so instead of pretending.

Steps on a debug build: Settings is untouched; in the **Community** tab pick *Demo role → CHW*
(debug only), tap *Create synthetic demo area for me*, register or search a synthetic person in the
Patients tab, open them, tap *Community health worker → Open → New community screening*.

## Local setup (reproducible)

```bash
# JDK 17 (toolchain) and JDK 21 (runs Gradle) on PATH; Android SDK:
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
cd burundi-chw-android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleQaDebug -PmanifestEndpoint=https://<burundi-dev-manifest>/
./gradlew :app:testQaDebugUnitTest --tests "org.simple.clinic.chw.*"
./gradlew :app:connectedQaDebugAndroidTest   # needs an emulator/device
```
Without `-PmanifestEndpoint`, the app builds but cannot log in (default host is `.invalid` on purpose).

## Branding / applicationId (decision needed)

Changing `applicationId` from `org.simple.clinic` requires matching entries in each flavor's
`google-services.json`, i.e. a **new Firebase project owned by the Burundi programme** (or removing
Firebase). That is a third-party-service decision the instructions reserve for approval, so it was
not done. Proposed: `applicationId = "bi.<org>.chw"` (with existing `.qa`/`.debug` suffixes), app
name "<Programme name>", new launcher icon; keep `LICENSE` (MIT, Simple.org) and an attribution
line in Settings → About.

## Next slices

1. Gate `NewMedicalHistoryScreen` diagnosis questions for CHW role (show "not answered – clinician to confirm").
2. Show `FollowUpStatus` badges per patient in the work queue; reschedule from the queue.
3. Clinician referral inbox (read-only until server exists, then accept/complete).
4. After server: enable `BurundiChwServerSync`, wire `ChwSession.setServerRole` into user refresh,
   call `purgeOutsideScope` after assignment pulls, add `LoggedInUser.capability_burundi_programme_role` column (migration 125).
5. Instrumented UI tests on a device farm (offline enrolment, two-reading screening, urgent referral,
   follow-up, sync indicators, denied medication edit).

## Build & test results (this branch, 2026-11-10, cloud Linux container, no emulator)

| Check | Result |
|---|---|
| Baseline: pristine upstream `:app:assembleQaDebug` + sample upstream tests | PASS |
| `:app:assembleQaDebug` with Burundi changes | PASS (APK built) |
| `:app:testQaDebugUnitTest` — full upstream + CHW suite | **PASS: 2,472 tests, 395 suites, 0 failures, 0 skipped** (96 are new CHW tests) |
| `:app:compileQaDebugAndroidTestKotlin` (incl. `Migration124AndroidTest`) | PASS (compiled; **not run** — no KVM/emulator available) |
| Migration 124 SQL vs Room exported schema (columns, types, nullability, PKs, indices) | PASS (SQLite script) ; all 123 pre-existing tables byte-identical in schema JSON |
| UI/instrumentation tests for CHW flows | **Not written/run yet** — need a device; test tags are in place (`chw_save_screening`, `chw_screening_result`, …) |
| Screenshots of implemented screens | **Not produced** — no emulator in this environment |
| Server/API integration tests | **Not possible** — no server implementation |

Environment notes: Maven Central rate-limited (HTTP 429) during dependency resolution; builds were
retried automatically. Builds need JDK 17 installed for the Gradle toolchain.
