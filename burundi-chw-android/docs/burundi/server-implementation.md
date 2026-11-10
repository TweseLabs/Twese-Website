# Server implementation request (simple-server) — NOT IMPLEMENTED

The server repository was not supplied, so **no backend change exists**. This is a concrete change
request for `simpledotorg/simple-server` (Rails), written after reading its current structure
(`app/controllers/api/v3/sync_controller.rb`, `concerns/api/v3/sync_to_user.rb`,
`app/models/concerns/mergeable.rb`, `app/models/user.rb`). Run it on a Burundi fork with its own
database; never against an existing Simple deployment.

## 1. Models & migrations

```ruby
# db/migrate/2026xxxx_create_burundi_chw_tables.rb  (sketch)
create_table :community_areas, id: :uuid do |t|
  t.string :name, null: false
  t.string :province; t.string :commune; t.string :zone; t.string :colline
  t.uuid :parent_area_id; t.boolean :active, null: false, default: true
  t.timestamps; t.datetime :deleted_at
end
create_table :chw_assignments, id: :uuid do |t|
  t.uuid :chw_user_id, null: false; t.uuid :community_area_id, null: false
  t.uuid :supervising_facility_id, null: false
  t.date :start_date, null: false; t.date :end_date
  t.string :status, null: false
  t.timestamps; t.datetime :deleted_at
end
create_table :patient_chw_assignments, id: :uuid do |t|
  t.uuid :patient_id, null: false; t.uuid :chw_user_id, null: false; t.uuid :community_area_id
  t.datetime :assigned_at, null: false; t.string :status, null: false; t.string :end_reason
  t.uuid :previous_assignment_id
  t.datetime :device_created_at, null: false; t.datetime :device_updated_at, null: false
  t.timestamps; t.datetime :deleted_at
end
create_table :screening_episodes, id: :uuid do |t|
  t.uuid :patient_id, null: false; t.uuid :chw_user_id, null: false
  t.uuid :community_area_id; t.uuid :supervising_facility_id, null: false
  t.datetime :occurred_at, null: false; t.string :location_type, null: false
  t.string :consent_version, null: false
  t.integer :first_systolic, null: false; t.integer :first_diastolic, null: false
  t.integer :second_systolic; t.integer :second_diastolic; t.integer :minutes_between_readings
  t.string :danger_signs, array: true, null: false, default: []
  t.string :special_population, null: false
  t.string :workflow_outcome, null: false          # as computed on device
  t.string :server_workflow_outcome                # re-computed with approved server rules
  t.string :reason_codes, array: true, null: false, default: []
  t.string :decision_rule_version, null: false; t.boolean :decision_rule_approved, null: false
  t.uuid :referral_id; t.uuid :follow_up_task_id
  t.datetime :device_created_at, null: false; t.datetime :device_updated_at, null: false
  t.timestamps; t.datetime :deleted_at
end
create_table :chw_referrals, id: :uuid do |t|  # + referral_status_events (append-only history)
  # columns per api-contract.md; device_created_at/device_updated_at; acknowledged_at, completed_at
end
create_table :chw_follow_up_tasks, id: :uuid do |t| … end
create_table :chw_contacts, id: :uuid do |t| … end
```
Indexes on every `patient_id`, `chw_user_id`, `destination_facility_id`, `updated_at` (pull cursor).
Each model `include Mergeable` and has a `for_sync` scope (`with_discarded`), as upstream models do.

`referral_status_events(id, referral_id, from_status, to_status, actor_user_id, actor_role, occurred_at, received_at)`
is append-only and is the authoritative status history.

## 2. User role

- `users.burundi_programme_role` (string, nullable, validated in `chw clinician field_coordinator program_admin`).
- Add to `User::APP_USER_CAPABILITIES`-style serialization so the app user payload exposes
  `capabilities.burundi_programme_role`.
- Set only by programme admins through the admin dashboard, with an audit log entry.

## 3. Controllers & routes

```ruby
# config/routes.rb, inside scope "/v4"
scope :burundi do
  %w[screening_episodes referrals chw_follow_up_tasks chw_contacts patient_chw_assignments].each do |r|
    get  "#{r}/sync", to: "burundi/#{r}#sync_to_user"
    post "#{r}/sync", to: "burundi/#{r}#sync_from_user"
  end
  get "community_areas/sync", to: "burundi/community_areas#sync_to_user"
  get "chw_assignments/sync",  to: "burundi/chw_assignments#sync_to_user"
end
```
`Api::V4::Burundi::ScreeningEpisodesController < Api::V4::SyncController` with
`__sync_from_user__(params.require(:records).map { permit … })` and
`__sync_to_user__("records")`. Override `model_sync_scope` / `records_to_sync` (do **not** use the
default facility/region scoping — CHW scope is narrower):

```ruby
def records_to_sync
  scope = model.for_sync.updated_on_server_since(processed_since, limit)
  case current_user.burundi_programme_role
  when "chw"       then scope.where(patient_id: PatientChwAssignment.current.where(chw_user_id: current_user.id).select(:patient_id))
  when "clinician" then scope.where(supervising_facility_id: current_facility.id)   # per-resource column
  else scope.none
  end
end
```

## 4. Authorization (must be enforced server-side; client checks are UX only)

| Action | chw | clinician | field_coordinator | program_admin |
|---|---|---|---|---|
| Push screening / contact / task | own + assigned patients only | ✗ | ✗ | ✗ |
| Push referral (create, cancel before received) | ✓ (assigned patients) | — | — | — |
| Referral → accepted / unable_to_contact / completed | ✗ | ✓ (destination facility) | ✗ | ✗ |
| Referral → closed_unresolved | ✗ | ✓ | ✓ (supervised areas) | ✗ |
| Write `prescription_drugs`, `medical_histories.diagnosed_with_hypertension` | **✗ (reject with 403 / validation error)** | ✓ | ✗ | ✗ |
| Reassign patient ↔ CHW | ✗ | ✗ | ✓ (audit) | ✓ (audit) |
| Pull patient-level data outside assignment | ✗ | facility only | aggregates only by default | ✗ by default |
| Patient-list export | ✗ | ✗ | ✗ | only with documented justification |

Concretely: add a guard to `Api::V3::PrescriptionDrugsController#merge_if_valid` and
`MedicalHistoriesController` that returns `errors_hash` when `current_user.burundi_programme_role == "chw"`.

## 5. Revocation / reassignment / purge

- On CHW revocation (`sync_approval_status` → denied) the existing 401 flow logs the device out;
  the app then clears data (`AppDatabase.clearAppData`, which includes CHW tables).
- On patient reassignment, the server ends the old `patient_chw_assignments` row; the next pull
  delivers it; the client calls `ChwRepository.purgeOutsideScope(userId)` which deletes only
  already-synced rows for patients no longer in scope (pending rows are kept and uploaded, and the
  server rejects them if no longer authorised, so nothing is silently lost).
- Offline access expiry: server issues access tokens with a programme-defined max offline age;
  the app already enforces app lock/PIN locally.

## 6. Integration tests to write (RSpec request specs)

1. CHW pushes a screening for an assigned patient → 200, no errors; repeat push → no duplicate.
2. CHW pushes a screening for an unassigned patient → per-record error.
3. CHW posts a prescription drug → per-record error; nothing persisted.
4. CHW sets referral `accepted` → error; clinician does it → ok, status event written.
5. Clinician completes referral without `outcome_code` → error.
6. CHW pull returns only assigned patients' records; another CHW's records absent.
7. Reassignment: old CHW's next pull contains ended assignment; their further pushes for that patient are rejected.
8. Idempotent retry of the same batch after a simulated timeout → identical server state.
9. Out-of-order updates: older `updated_at` does not overwrite newer.
