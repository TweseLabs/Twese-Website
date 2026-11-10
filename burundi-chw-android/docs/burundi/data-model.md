# Data model — Burundi CHW module (Room schema v124)

`Migration_124` (`app/src/main/java/org/simple/clinic/storage/migrations/Migration_124.kt`) is
**purely additive**: seven new tables, no change to the 33 upstream tables (verified by diffing
`app/schemas/.../123.json` and `124.json`: all pre-existing entities identical). The migration SQL
was checked against Room's exported schema (columns, affinity, nullability, primary key, indices)
with an SQLite script; `Migration124AndroidTest` covers it on-device (needs an emulator to run).

All timestamps are UTC ISO-8601 TEXT. Enums are stored as stable snake_case `code`s (the same codes
used on the wire); unknown codes from a newer server map to a safe `unknown` value.

Common columns on synced tables: `createdAt`, `updatedAt` (device bookkeeping, used for
last-writer-wins on the server via `device_updated_at`), `deletedAt` (logical delete), `syncStatus`
(`PENDING` | `DONE` | `INVALID`). The "event" time is separate (`occurredAt` / `requestedAt` /
`assignedAt`) — i.e. *recordedAt* vs sync time are distinct.

## Tables

### CommunityArea (pull-only)
`id`, `name`, `province`, `commune`, `zone`, `colline`, `parentAreaId`, `active`, timestamps.

### ChwAssignment (pull-only)
`id`, `chwUserId`, `communityAreaId`, `supervisingFacilityId`, `startDate`, `endDate?`,
`status` (`pending_server_confirmation|active|suspended|ended`), timestamps. Index `chwUserId`.

### PatientChwAssignment (push + pull)
`id`, `patientId`, `chwUserId`, `communityAreaId?`, `assignedAt`, `status`, `endReason?`
(`reassigned|transferred_out|declined_chw_follow_up|moved_away|died`), `previousAssignmentId?`,
timestamps, `syncStatus`. Reassignment = end old row + new row pointing at it (history kept).
Device-created rows start `pending_server_confirmation`; only the server may set `active`.
**Does not touch** `Patient.registeredFacilityId` / `assignedFacilityId`.

### ScreeningEpisode (push + pull)
`id`, `patientId`, `chwUserId`, `communityAreaId?`, `supervisingFacilityId`, `occurredAt`,
`locationType`, `consentVersion`, `firstSystolic`, `firstDiastolic`, `secondSystolic?`,
`secondDiastolic?`, `minutesBetweenReadings?`, `dangerSigns` (sorted CSV of codes; `none` = asked,
none present; empty = checklist not completed), `specialPopulation`, `outcome`
(`normal_screening|repeat_required|clinical_confirmation_required|urgent_clinical_assessment|needs_clinician_review`),
`reasonCodes` (CSV, audit of the rule path), `decisionRuleVersion`, `decisionRuleApproved`,
`referralId?`, `followUpTaskId?`, timestamps, `syncStatus`.
Never written to `BloodPressureMeasurement` or `MedicalHistory`.

### ChwReferral (push + pull)
`id`, `patientId`, `screeningEpisodeId?`, `sourceUserId`, `originatingAreaId?`,
`destinationFacilityId`, `priority` (`routine|priority|urgent`), `reasonCodes`, `status`,
`requestedAt`, `dueDate`, `acknowledgedAt?`, `completedAt?`, `clinicianUserId?`, `outcomeCode?`,
`nextActionCode?` (structured clinician instruction back to CHW), timestamps, `syncStatus`.
Table is named `ChwReferral` to avoid a generic `Referral` name clash with future upstream work.

Status machine (`ReferralStateMachine`, mirrored server-side):

```
draft ──CHW──▶ submitted_pending_sync ──SERVER──▶ received ──CLINICIAN──▶ accepted ──CLINICIAN(+outcome)──▶ completed
  │                    │                              │  ╲                    │  ▲
  └─CHW─▶ cancelled ◀──┘ (CHW, before received)       │   ╲CLINICIAN          ▼  │CLINICIAN
                                                       │    ▶ unable_to_contact ─┘
                     CLINICIAN / FIELD_COORDINATOR:  received|accepted|unable_to_contact ──▶ closed_unresolved
```
`received` can only be set by the server; the device can never show "received by clinic" for a
referral it has not pulled back in that state.

### ChwFollowUpTask (push + pull)
`id`, `patientId`, `chwUserId`, `communityAreaId?`, `type`
(`repeat_screening|check_clinic_confirmation|urgent_referral_check|routine_follow_up|rescreen`),
`dueDate`, `status` (`open|completed|cancelled`), `screeningEpisodeId?`, `referralId?`,
`completedByContactId?`, `rescheduleCount`, timestamps, `syncStatus`.
Index (`chwUserId`, `status`, `dueDate`) backs the work queue.

### ChwContact (push + pull)
`id`, `patientId`, `chwUserId`, `taskId?`, `occurredAt`, `method`
(`home_visit|phone_call|community_point`), `outcome`
(`reached|not_at_home|phone_unreachable|declined|transferred_out|moved_away`), `systolic?`,
`diastolic?`, `dangerSigns`, `medicineAvailability` (patient-reported:
`has_taking|has_not_taking|ran_out|could_not_get|not_on_medicine|not_asked`), `sideEffectConcern?`,
`nextDueDate?`, `note?` (≤280 chars, UI asks for no names/diagnoses), timestamps, `syncStatus`.

## Follow-up status categories (computed, not stored)

`FollowUpStatusClassifier` → `active | missed_clinic_visit | missed_chw_contact | unreachable |
declined | transferred | lost_to_follow_up`. *Lost* requires ≥3 failed attempts on ≥3 distinct days
over ≥90 days since last successful contact (draft policy, needs approval). One missed visit is
never "lost".

## Indicators (definitions; computation is server-side and server-blocked)

Each is a separate numerator/denominator; screening *visits* are never counted as unique people,
and BP control among retained patients is never presented as population control.

| # | Indicator | Numerator | Denominator | Notes |
|---|---|---|---|---|
| 1 | Eligible adults in area | — | Estimated eligible adults per `CommunityArea` (census/survey estimate, entered by programme) | Never inferred from app users |
| 2 | Adults screened | Distinct `patientId` with ≥1 `ScreeningEpisode` in period | #1 | Distinct people |
| 3 | Elevated screens needing confirmation | Distinct people whose latest episode in period is `clinical_confirmation_required`/`urgent`/`needs_clinician_review` | #2 | |
| 4 | Confirmation within target | People in #3 with referral `completed` within N days (approved target) | #3 | Report incomplete separately |
| 5 | Diagnosed & enrolled in treatment | People in #4 with clinician-recorded HTN diagnosis + ≥1 prescription | #4 confirmed HTN | Uses clinic records, not screening |
| 6 | Retention at 3/6/12 m | People with a clinic BP or CHW reached-contact in window | Enrolled ≥3/6/12 m ago | |
| 7 | BP control | Treated patients whose latest clinic BP in window is controlled | Treated patients **with a visit** in window; patients **without** a visit reported separately as "no recent BP" | |
| 8 | Urgent referral resolution | Urgent referrals `completed` (time-to-completion) / still open past due | All urgent referrals in period | |
| 9 | Medicine availability | `ChwContact` reports `ran_out`/`could_not_get` by clinic | Reached contacts with medicine asked | Patient-reported; complements clinic drug-stock data |
| 10 | Data quality | Duplicates, implausible-reading rejections, missing checklist, `INVALID` syncs, PENDING backlog age | Respective totals | |
