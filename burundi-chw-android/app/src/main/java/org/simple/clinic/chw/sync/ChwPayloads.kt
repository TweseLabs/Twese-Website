package org.simple.clinic.chw.sync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.simple.clinic.chw.AssignmentEndReason
import org.simple.clinic.chw.AssignmentStatus
import org.simple.clinic.chw.CodeSets
import org.simple.clinic.chw.ContactMethod
import org.simple.clinic.chw.ContactOutcome
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.ReportedMedicineAvailability
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.area.ChwAssignment
import org.simple.clinic.chw.area.CommunityArea
import org.simple.clinic.chw.area.PatientChwAssignment
import org.simple.clinic.chw.codeToEnum
import org.simple.clinic.chw.followup.ChwContact
import org.simple.clinic.chw.followup.ChwFollowUpTask
import org.simple.clinic.chw.referral.Referral
import org.simple.clinic.chw.referral.ReferralStatus
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningEpisode
import org.simple.clinic.chw.screening.ScreeningOutcome
import org.simple.clinic.chw.screening.SpecialPopulation
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/*
 * Wire format for the Burundi CHW API, version 1 (docs/burundi/api-contract.md).
 * Enums travel as stable snake_case codes. All ids are client-generated UUIDs so that retries are
 * idempotent (server upserts by id). `recorded_at`/`occurred_at` = when it happened on the device;
 * `created_at`/`updated_at` = record bookkeeping; the server keeps its own `received_at`.
 */

private fun dangerSignsFrom(codes: List<String>): Set<DangerSign> = codes.mapNotNull(DangerSign::fromCode).toSet()

@JsonClass(generateAdapter = true)
data class ScreeningEpisodePayload(
    @Json(name = "id") val id: UUID,
    @Json(name = "patient_id") val patientId: UUID,
    @Json(name = "chw_user_id") val chwUserId: UUID,
    @Json(name = "community_area_id") val communityAreaId: UUID?,
    @Json(name = "supervising_facility_id") val supervisingFacilityId: UUID,
    @Json(name = "occurred_at") val occurredAt: Instant,
    @Json(name = "location_type") val locationType: String,
    @Json(name = "consent_version") val consentVersion: String,
    @Json(name = "first_systolic") val firstSystolic: Int,
    @Json(name = "first_diastolic") val firstDiastolic: Int,
    @Json(name = "second_systolic") val secondSystolic: Int?,
    @Json(name = "second_diastolic") val secondDiastolic: Int?,
    @Json(name = "minutes_between_readings") val minutesBetweenReadings: Int?,
    @Json(name = "danger_signs") val dangerSigns: List<String>,
    @Json(name = "special_population") val specialPopulation: String,
    @Json(name = "workflow_outcome") val outcome: String,
    @Json(name = "reason_codes") val reasonCodes: List<String>,
    @Json(name = "decision_rule_version") val decisionRuleVersion: String,
    @Json(name = "decision_rule_approved") val decisionRuleApproved: Boolean,
    @Json(name = "referral_id") val referralId: UUID?,
    @Json(name = "follow_up_task_id") val followUpTaskId: UUID?,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "deleted_at") val deletedAt: Instant?
) {
  fun toDatabaseModel(syncStatus: SyncStatus) = ScreeningEpisode(
      id, patientId, chwUserId, communityAreaId, supervisingFacilityId, occurredAt,
      codeToEnum(locationType, ScreeningLocationType.UNKNOWN), consentVersion,
      firstSystolic, firstDiastolic, secondSystolic, secondDiastolic, minutesBetweenReadings,
      dangerSignsFrom(dangerSigns), SpecialPopulation.fromCode(specialPopulation), ScreeningOutcome.fromCode(outcome),
      CodeSets.encode(reasonCodes), decisionRuleVersion, decisionRuleApproved, referralId, followUpTaskId,
      Timestamps(createdAt, updatedAt, deletedAt), syncStatus
  )

  companion object {
    fun from(e: ScreeningEpisode) = ScreeningEpisodePayload(
        e.id, e.patientId, e.chwUserId, e.communityAreaId, e.supervisingFacilityId, e.occurredAt,
        e.locationType.code, e.consentVersion, e.firstSystolic, e.firstDiastolic, e.secondSystolic,
        e.secondDiastolic, e.minutesBetweenReadings, e.dangerSigns.map { it.code }.sorted(), e.specialPopulation.code,
        e.outcome.code, CodeSets.decode(e.reasonCodes), e.decisionRuleVersion, e.decisionRuleApproved,
        e.referralId, e.followUpTaskId, e.timestamps.createdAt, e.timestamps.updatedAt, e.timestamps.deletedAt
    )
  }
}

@JsonClass(generateAdapter = true)
data class ReferralPayload(
    @Json(name = "id") val id: UUID,
    @Json(name = "patient_id") val patientId: UUID,
    @Json(name = "screening_episode_id") val screeningEpisodeId: UUID?,
    @Json(name = "source_user_id") val sourceUserId: UUID,
    @Json(name = "originating_area_id") val originatingAreaId: UUID?,
    @Json(name = "destination_facility_id") val destinationFacilityId: UUID,
    @Json(name = "priority") val priority: String,
    @Json(name = "reason_codes") val reasonCodes: List<String>,
    @Json(name = "status") val status: String,
    @Json(name = "requested_at") val requestedAt: Instant,
    @Json(name = "due_date") val dueDate: LocalDate,
    @Json(name = "acknowledged_at") val acknowledgedAt: Instant?,
    @Json(name = "completed_at") val completedAt: Instant?,
    @Json(name = "clinician_user_id") val clinicianUserId: UUID?,
    @Json(name = "outcome_code") val outcomeCode: String?,
    @Json(name = "next_action_code") val nextActionCode: String?,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "deleted_at") val deletedAt: Instant?
) {
  fun toDatabaseModel(syncStatus: SyncStatus) = Referral(
      id, patientId, screeningEpisodeId, sourceUserId, originatingAreaId, destinationFacilityId,
      codeToEnum(priority, ReferralPriority.UNKNOWN), CodeSets.encode(reasonCodes),
      codeToEnum(status, ReferralStatus.UNKNOWN), requestedAt, dueDate, acknowledgedAt, completedAt,
      clinicianUserId, outcomeCode, nextActionCode, Timestamps(createdAt, updatedAt, deletedAt), syncStatus
  )

  companion object {
    fun from(r: Referral) = ReferralPayload(
        r.id, r.patientId, r.screeningEpisodeId, r.sourceUserId, r.originatingAreaId, r.destinationFacilityId,
        r.priority.code, CodeSets.decode(r.reasonCodes), r.status.code, r.requestedAt, r.dueDate,
        r.acknowledgedAt, r.completedAt, r.clinicianUserId, r.outcomeCode, r.nextActionCode,
        r.timestamps.createdAt, r.timestamps.updatedAt, r.timestamps.deletedAt
    )
  }
}

@JsonClass(generateAdapter = true)
data class ChwFollowUpTaskPayload(
    @Json(name = "id") val id: UUID,
    @Json(name = "patient_id") val patientId: UUID,
    @Json(name = "chw_user_id") val chwUserId: UUID,
    @Json(name = "community_area_id") val communityAreaId: UUID?,
    @Json(name = "task_type") val type: String,
    @Json(name = "due_date") val dueDate: LocalDate,
    @Json(name = "status") val status: String,
    @Json(name = "screening_episode_id") val screeningEpisodeId: UUID?,
    @Json(name = "referral_id") val referralId: UUID?,
    @Json(name = "completed_by_contact_id") val completedByContactId: UUID?,
    @Json(name = "reschedule_count") val rescheduleCount: Int,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "deleted_at") val deletedAt: Instant?
) {
  fun toDatabaseModel(syncStatus: SyncStatus) = ChwFollowUpTask(
      id, patientId, chwUserId, communityAreaId, codeToEnum(type, FollowUpTaskType.UNKNOWN), dueDate,
      codeToEnum(status, FollowUpTaskStatus.UNKNOWN), screeningEpisodeId, referralId, completedByContactId,
      rescheduleCount, Timestamps(createdAt, updatedAt, deletedAt), syncStatus
  )

  companion object {
    fun from(t: ChwFollowUpTask) = ChwFollowUpTaskPayload(
        t.id, t.patientId, t.chwUserId, t.communityAreaId, t.type.code, t.dueDate, t.status.code,
        t.screeningEpisodeId, t.referralId, t.completedByContactId, t.rescheduleCount,
        t.timestamps.createdAt, t.timestamps.updatedAt, t.timestamps.deletedAt
    )
  }
}

@JsonClass(generateAdapter = true)
data class ChwContactPayload(
    @Json(name = "id") val id: UUID,
    @Json(name = "patient_id") val patientId: UUID,
    @Json(name = "chw_user_id") val chwUserId: UUID,
    @Json(name = "task_id") val taskId: UUID?,
    @Json(name = "occurred_at") val occurredAt: Instant,
    @Json(name = "contact_method") val method: String,
    @Json(name = "contact_outcome") val outcome: String,
    @Json(name = "systolic") val systolic: Int?,
    @Json(name = "diastolic") val diastolic: Int?,
    @Json(name = "danger_signs") val dangerSigns: List<String>,
    @Json(name = "reported_medicine_availability") val medicineAvailability: String,
    @Json(name = "side_effect_concern") val sideEffectConcern: Boolean?,
    @Json(name = "next_due_date") val nextDueDate: LocalDate?,
    @Json(name = "note") val note: String?,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "deleted_at") val deletedAt: Instant?
) {
  fun toDatabaseModel(syncStatus: SyncStatus) = ChwContact(
      id, patientId, chwUserId, taskId, occurredAt, codeToEnum(method, ContactMethod.UNKNOWN),
      codeToEnum(outcome, ContactOutcome.UNKNOWN), systolic, diastolic, dangerSignsFrom(dangerSigns),
      codeToEnum(medicineAvailability, ReportedMedicineAvailability.UNKNOWN), sideEffectConcern, nextDueDate, note,
      Timestamps(createdAt, updatedAt, deletedAt), syncStatus
  )

  companion object {
    fun from(c: ChwContact) = ChwContactPayload(
        c.id, c.patientId, c.chwUserId, c.taskId, c.occurredAt, c.method.code, c.outcome.code, c.systolic,
        c.diastolic, c.dangerSigns.map { it.code }.sorted(), c.medicineAvailability.code, c.sideEffectConcern,
        c.nextDueDate, c.note, c.timestamps.createdAt, c.timestamps.updatedAt, c.timestamps.deletedAt
    )
  }
}

@JsonClass(generateAdapter = true)
data class PatientChwAssignmentPayload(
    @Json(name = "id") val id: UUID,
    @Json(name = "patient_id") val patientId: UUID,
    @Json(name = "chw_user_id") val chwUserId: UUID,
    @Json(name = "community_area_id") val communityAreaId: UUID?,
    @Json(name = "assigned_at") val assignedAt: Instant,
    @Json(name = "status") val status: String,
    @Json(name = "end_reason") val endReason: String?,
    @Json(name = "previous_assignment_id") val previousAssignmentId: UUID?,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "deleted_at") val deletedAt: Instant?
) {
  fun toDatabaseModel(syncStatus: SyncStatus) = PatientChwAssignment(
      id, patientId, chwUserId, communityAreaId, assignedAt, codeToEnum(status, AssignmentStatus.UNKNOWN),
      endReason?.let { codeToEnum(it, AssignmentEndReason.UNKNOWN) }, previousAssignmentId,
      Timestamps(createdAt, updatedAt, deletedAt), syncStatus
  )

  companion object {
    fun from(a: PatientChwAssignment) = PatientChwAssignmentPayload(
        a.id, a.patientId, a.chwUserId, a.communityAreaId, a.assignedAt, a.status.code, a.endReason?.code,
        a.previousAssignmentId, a.timestamps.createdAt, a.timestamps.updatedAt, a.timestamps.deletedAt
    )
  }
}

@JsonClass(generateAdapter = true)
data class CommunityAreaPayload(
    @Json(name = "id") val id: UUID,
    @Json(name = "name") val name: String,
    @Json(name = "province") val province: String?,
    @Json(name = "commune") val commune: String?,
    @Json(name = "zone") val zone: String?,
    @Json(name = "colline") val colline: String?,
    @Json(name = "parent_area_id") val parentAreaId: UUID?,
    @Json(name = "active") val active: Boolean,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "deleted_at") val deletedAt: Instant?
) {
  fun toDatabaseModel() = CommunityArea(
      id, name, province, commune, zone, colline, parentAreaId, active, Timestamps(createdAt, updatedAt, deletedAt)
  )
}

@JsonClass(generateAdapter = true)
data class ChwAssignmentPayload(
    @Json(name = "id") val id: UUID,
    @Json(name = "chw_user_id") val chwUserId: UUID,
    @Json(name = "community_area_id") val communityAreaId: UUID,
    @Json(name = "supervising_facility_id") val supervisingFacilityId: UUID,
    @Json(name = "start_date") val startDate: LocalDate,
    @Json(name = "end_date") val endDate: LocalDate?,
    @Json(name = "status") val status: String,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "updated_at") val updatedAt: Instant,
    @Json(name = "deleted_at") val deletedAt: Instant?
) {
  fun toDatabaseModel() = ChwAssignment(
      id, chwUserId, communityAreaId, supervisingFacilityId, startDate, endDate,
      codeToEnum(status, AssignmentStatus.UNKNOWN), Timestamps(createdAt, updatedAt, deletedAt)
  )
}

/** Generic push/pull envelopes; `records` keeps every CHW endpoint shaped the same. */
@JsonClass(generateAdapter = true)
data class ChwPushRequest<P>(
    @Json(name = "api_version") val apiVersion: Int = CHW_API_VERSION,
    @Json(name = "records") val records: List<P>
)

@JsonClass(generateAdapter = true)
data class ChwPullResponse<P>(
    @Json(name = "records") val records: List<P>,
    @Json(name = "process_token") val processToken: String
)

const val CHW_API_VERSION = 1
