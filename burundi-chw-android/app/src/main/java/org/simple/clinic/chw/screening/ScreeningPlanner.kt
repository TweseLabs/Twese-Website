package org.simple.clinic.chw.screening

import org.simple.clinic.chw.AssignmentStatus
import org.simple.clinic.chw.CodeSets
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.access.ChwAccessPolicy
import org.simple.clinic.chw.access.ChwPermission
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.area.PatientChwAssignment
import org.simple.clinic.chw.followup.ChwFollowUpTask
import org.simple.clinic.chw.referral.Referral
import org.simple.clinic.chw.referral.ReferralStatus
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ScreeningRequest(
    val patientId: UUID,
    val chwUserId: UUID,
    val role: ChwRole,
    val communityAreaId: UUID?,
    val supervisingFacilityId: UUID,
    val occurredAt: Instant,
    val today: LocalDate,
    val locationType: ScreeningLocationType,
    val consentVersion: String,
    val consentGiven: Boolean,
    val input: ScreeningInput
)

/** Everything one screening writes. Persisted atomically by [org.simple.clinic.chw.ChwRepository]. */
data class ScreeningPlan(
    val episode: ScreeningEpisode,
    val referral: Referral?,
    val newTask: ChwFollowUpTask,
    val newPatientAssignment: PatientChwAssignment?,
    /** Open repeat/rescreen tasks of this CHW that this screening fulfils. */
    val tasksToComplete: List<ChwFollowUpTask>
)

sealed interface PlanResult {
  data class Planned(val plan: ScreeningPlan, val classification: ScreeningClassification.Result) : PlanResult
  data class Rejected(val reason: RejectionReason) : PlanResult
}

enum class RejectionReason {
  NOT_PERMITTED_FOR_ROLE,
  CONSENT_NOT_RECORDED,
  PATIENT_ASSIGNED_TO_ANOTHER_CHW,
  INVALID_READING,
  FIRST_READING_REQUIRED
}

/**
 * Pure planning of a screening: permission + consent checks, triage classification, and creation
 * of the episode, any referral, the next CHW task and (if needed) a pending patient assignment.
 */
class ScreeningPlanner(
    private val classifier: ScreeningClassifier,
    private val newId: () -> UUID
) {

  fun plan(
      request: ScreeningRequest,
      currentAssignments: List<PatientChwAssignment>,
      openTasksForPatient: List<ChwFollowUpTask>
  ): PlanResult {
    if (!ChwAccessPolicy.isAllowed(request.role, ChwPermission.RECORD_COMMUNITY_SCREENING)) {
      return PlanResult.Rejected(RejectionReason.NOT_PERMITTED_FOR_ROLE)
    }
    if (!request.consentGiven) return PlanResult.Rejected(RejectionReason.CONSENT_NOT_RECORDED)

    val activeAssignees = currentAssignments.filter { it.isCurrent }.map { it.chwUserId }.toSet()
    if (activeAssignees.isNotEmpty() && request.chwUserId !in activeAssignees) {
      return PlanResult.Rejected(RejectionReason.PATIENT_ASSIGNED_TO_ANOTHER_CHW)
    }

    val first = request.input.first ?: return PlanResult.Rejected(RejectionReason.FIRST_READING_REQUIRED)
    val classification = when (val c = classifier.classify(request.input)) {
      is ScreeningClassification.InvalidReading -> return PlanResult.Rejected(RejectionReason.INVALID_READING)
      is ScreeningClassification.Result -> c
    }

    val timestamps = Timestamps(createdAt = request.occurredAt, updatedAt = request.occurredAt, deletedAt = null)
    val outcome = classification.outcome
    val dueDate = request.today.plusDays(classifier.nextActionDueIn(outcome).toDays())
    val episodeId = newId()

    val referral = referralPriorityFor(outcome)?.let { priority ->
      Referral(
          id = newId(),
          patientId = request.patientId,
          screeningEpisodeId = episodeId,
          sourceUserId = request.chwUserId,
          originatingAreaId = request.communityAreaId,
          destinationFacilityId = request.supervisingFacilityId,
          priority = priority,
          reasonCodes = CodeSets.encode(listOf(outcome.code) + classification.reasons.map { it.code }),
          status = ReferralStatus.SUBMITTED_PENDING_SYNC,
          requestedAt = request.occurredAt,
          dueDate = dueDate,
          acknowledgedAt = null,
          completedAt = null,
          clinicianUserId = null,
          outcomeCode = null,
          nextActionCode = null,
          timestamps = timestamps,
          syncStatus = SyncStatus.PENDING
      )
    }

    val task = ChwFollowUpTask(
        id = newId(),
        patientId = request.patientId,
        chwUserId = request.chwUserId,
        communityAreaId = request.communityAreaId,
        type = taskTypeFor(outcome),
        dueDate = dueDate,
        status = FollowUpTaskStatus.OPEN,
        screeningEpisodeId = episodeId,
        referralId = referral?.id,
        completedByContactId = null,
        rescheduleCount = 0,
        timestamps = timestamps,
        syncStatus = SyncStatus.PENDING
    )

    val episode = ScreeningEpisode(
        id = episodeId,
        patientId = request.patientId,
        chwUserId = request.chwUserId,
        communityAreaId = request.communityAreaId,
        supervisingFacilityId = request.supervisingFacilityId,
        occurredAt = request.occurredAt,
        locationType = request.locationType,
        consentVersion = request.consentVersion,
        firstSystolic = first.systolic,
        firstDiastolic = first.diastolic,
        secondSystolic = request.input.second?.systolic,
        secondDiastolic = request.input.second?.diastolic,
        minutesBetweenReadings = request.input.minutesBetweenReadings?.toInt(),
        dangerSigns = request.input.dangerSigns,
        specialPopulation = request.input.specialPopulation,
        outcome = outcome,
        reasonCodes = CodeSets.encode(classification.reasons.map { it.code }),
        decisionRuleVersion = classification.protocolVersion,
        decisionRuleApproved = classification.protocolApproved,
        referralId = referral?.id,
        followUpTaskId = task.id,
        timestamps = timestamps,
        syncStatus = SyncStatus.PENDING
    )

    val newAssignment = if (activeAssignees.isEmpty()) {
      PatientChwAssignment(
          id = newId(),
          patientId = request.patientId,
          chwUserId = request.chwUserId,
          communityAreaId = request.communityAreaId,
          assignedAt = request.occurredAt,
          status = AssignmentStatus.PENDING_SERVER_CONFIRMATION,
          endReason = null,
          previousAssignmentId = null,
          timestamps = timestamps,
          syncStatus = SyncStatus.PENDING
      )
    } else null

    val fulfilled = openTasksForPatient
        .filter { it.chwUserId == request.chwUserId && it.status == FollowUpTaskStatus.OPEN }
        .filter { it.type == FollowUpTaskType.REPEAT_SCREENING || it.type == FollowUpTaskType.RESCREEN }
        .map {
          it.copy(
              status = FollowUpTaskStatus.COMPLETED,
              timestamps = it.timestamps.copy(updatedAt = request.occurredAt),
              syncStatus = SyncStatus.PENDING
          )
        }

    return PlanResult.Planned(
        plan = ScreeningPlan(episode, referral, task, newAssignment, fulfilled),
        classification = classification
    )
  }

  companion object {
    fun referralPriorityFor(outcome: ScreeningOutcome): ReferralPriority? = when (outcome) {
      ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT -> ReferralPriority.URGENT
      ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED -> ReferralPriority.ROUTINE
      ScreeningOutcome.NEEDS_CLINICIAN_REVIEW -> ReferralPriority.ROUTINE
      ScreeningOutcome.REPEAT_REQUIRED, ScreeningOutcome.NORMAL_SCREENING -> null
    }

    fun taskTypeFor(outcome: ScreeningOutcome): FollowUpTaskType = when (outcome) {
      ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT -> FollowUpTaskType.URGENT_REFERRAL_CHECK
      ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED -> FollowUpTaskType.CHECK_CLINIC_CONFIRMATION
      ScreeningOutcome.NEEDS_CLINICIAN_REVIEW -> FollowUpTaskType.CHECK_CLINIC_CONFIRMATION
      ScreeningOutcome.REPEAT_REQUIRED -> FollowUpTaskType.REPEAT_SCREENING
      ScreeningOutcome.NORMAL_SCREENING -> FollowUpTaskType.RESCREEN
    }
  }
}
