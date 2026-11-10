# API contract — Burundi CHW sync, version 1

Status: **specified and implemented on the Android client; NOT implemented on any server.**
The client only calls these endpoints when `Feature.BurundiChwServerSync` is enabled (default
`false`, no remote-config key). Client source: `app/src/main/java/org/simple/clinic/chw/sync/`.
Round-trip JSON tests: `app/src/test/java/org/simple/clinic/chw/sync/ChwPayloadTest.kt`.

Existing upstream endpoints and payloads are **unchanged**.

## Conventions

- Base path: the deployment's existing API base (`…/api/`), endpoints under `v4/burundi/…`.
- Header on every CHW request: `X-Burundi-Chw-Api-Version: 1` (plus all existing Simple headers:
  `Authorization: Bearer <access_token>`, `X-USER-ID`, `X-FACILITY-ID`, `X-SYNC-REGION-ID`, …).
- Ids are client-generated UUIDv4. **Push is an idempotent upsert by id**; retrying a batch must not
  create duplicates.
- Conflict rule (same as simple-server `Mergeable`): the server keeps the version with the greater
  `updated_at` (stored server-side as `device_updated_at`), *except* that referral `status`
  transitions are validated against the state machine below; an invalid transition is returned as a
  validation error and the device marks that record `INVALID`.
- Server stamps its own `received_at`/`created_at` (server) and keeps full status history.
- Enum values are snake_case codes. Unknown codes must be tolerated by both sides.
- Pull: `GET …/sync?limit=N&process_token=T` → `{"records": [...], "process_token": "…"}`; the client
  pages until a page shorter than `limit` is returned.
- Push: `POST …/sync` with `{"api_version": 1, "records": [...]}` → `{"errors": [{"id": "<uuid>", "schema": ["…"]}]}`
  (same error shape as upstream `DataPushResponse`).
- `403` for a record outside the caller's scope is returned as a per-record error, never as a
  silent success.

## Endpoints

| Resource | Push (CHW) | Pull | Pull scope (server-enforced) |
|---|---|---|---|
| `community_areas` | — | `GET v4/burundi/community_areas/sync` | Areas of the caller's active assignments (CHW) / supervised areas (coordinator) |
| `chw_assignments` | — | `GET v4/burundi/chw_assignments/sync` | Caller's own assignments |
| `patient_chw_assignments` | `POST v4/burundi/patient_chw_assignments/sync` | `GET …` | CHW: rows where `chw_user_id = caller`; clinician: patients of their facility |
| `screening_episodes` | `POST v4/burundi/screening_episodes/sync` | `GET …` | CHW: assigned patients only; clinician: `supervising_facility_id` = their facility |
| `referrals` | `POST v4/burundi/referrals/sync` | `GET …` | CHW: referrals they created or for assigned patients; clinician: `destination_facility_id` = their facility |
| `chw_follow_up_tasks` | `POST v4/burundi/chw_follow_up_tasks/sync` | `GET …` | CHW: own tasks |
| `chw_contacts` | `POST v4/burundi/chw_contacts/sync` | `GET …` | CHW: own contacts on assigned patients |

Client push order: patient_chw_assignments → screening_episodes → referrals → chw_follow_up_tasks →
chw_contacts (after the upstream patient sync, so `patient_id` exists server-side).

## Payload schemas (JSON)

### screening_episode
```json
{
  "id": "uuid", "patient_id": "uuid", "chw_user_id": "uuid", "community_area_id": "uuid|null",
  "supervising_facility_id": "uuid", "occurred_at": "2026-03-10T08:00:00Z",
  "location_type": "household|community_screening_point|other",
  "consent_version": "string",
  "first_systolic": 150, "first_diastolic": 95,
  "second_systolic": 146, "second_diastolic": 92, "minutes_between_readings": 3,
  "danger_signs": ["none"] ,
  "special_population": "none|pregnant_or_postpartum|under_18|unknown",
  "workflow_outcome": "normal_screening|repeat_required|clinical_confirmation_required|urgent_clinical_assessment|needs_clinician_review",
  "reason_codes": ["average_at_or_above_elevated_threshold"],
  "decision_rule_version": "draft-0.1-unapproved", "decision_rule_approved": false,
  "referral_id": "uuid|null", "follow_up_task_id": "uuid|null",
  "created_at": "…", "updated_at": "…", "deleted_at": null
}
```
Server validation: plausible ranges; `first_diastolic < first_systolic`; caller has CHW role and
an active or pending assignment for the patient; `workflow_outcome` is **advisory** (the server may
re-run the approved rule set and store both).

### referral
```json
{
  "id": "uuid", "patient_id": "uuid", "screening_episode_id": "uuid|null",
  "source_user_id": "uuid", "originating_area_id": "uuid|null",
  "destination_facility_id": "uuid", "priority": "routine|priority|urgent",
  "reason_codes": ["urgent_clinical_assessment", "danger_sign_reported"],
  "status": "draft|submitted_pending_sync|received|accepted|completed|unable_to_contact|cancelled|closed_unresolved",
  "requested_at": "…", "due_date": "2026-03-10",
  "acknowledged_at": null, "completed_at": null, "clinician_user_id": null,
  "outcome_code": null, "next_action_code": null,
  "created_at": "…", "updated_at": "…", "deleted_at": null
}
```
On first receipt of a `submitted_pending_sync` referral the server sets `status = received`,
`acknowledged_at` stays null until a clinician acts. Only clinicians may set
`accepted|unable_to_contact|completed`; `completed` requires `outcome_code`.

### chw_follow_up_task, chw_contact, patient_chw_assignment, community_area, chw_assignment
Field names are the snake_case of the columns in [`data-model.md`](data-model.md); exact Kotlin
definitions: `chw/sync/ChwPayloads.kt` (`ChwFollowUpTaskPayload` uses `task_type`;
`ChwContactPayload` uses `contact_method`, `contact_outcome`, `reported_medicine_availability`).

## User role (server-blocked)

Add to the existing logged-in-user payload `capabilities` object (today:
`{"can_teleconsult": "yes|no"}`) a field `"burundi_programme_role": "chw|clinician|field_coordinator|program_admin"`.
Client hook: `ChwSession.setServerRole(code)` — to be called from the user refresh path once the
field exists (requires adding a nullable column to `LoggedInUser` in a later migration).
Missing/unknown role ⇒ client treats user as `legacy_clinic_user` (upstream behaviour, no CHW tab
except in debug builds).

## Feature negotiation

The client sends `X-Burundi-Chw-Api-Version`. A server that does not support the version must
respond `426 Upgrade Required` (or `404` if the routes are absent); the client keeps records
`PENDING` (nothing is lost) and the UI continues to say "not yet sent".
