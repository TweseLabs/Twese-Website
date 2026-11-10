package org.simple.clinic.chw.followup

import org.simple.clinic.chw.CodeSets
import org.simple.clinic.chw.ContactMethod
import org.simple.clinic.chw.ContactOutcome
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.ReportedMedicineAvailability
import org.simple.clinic.chw.access.ChwAccessPolicy
import org.simple.clinic.chw.access.ChwPermission
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.area.PatientChwAssignment
import org.simple.clinic.chw.referral.Referral
import org.simple.clinic.chw.referral.ReferralStatus
import org.simple.clinic.chw.screening.BpReading
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningClassification
import org.simple.clinic.chw.screening.ScreeningClassifier
import org.simple.clinic.chw.screening.ScreeningInput
import org.simple.clinic.chw.screening.ScreeningOutcome
import org.simple.clinic.chw.screening.SpecialPopulation
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class FollowUpRequest(
    val patientId: UUID,
    val chwUserId: UUID,
    val role: ChwRole,
    val communityAreaId: UUID?,
    val supervisingFacilityId: UUID,
    val taskId: UUID?,
    val occurredAt: Instant,
    val today: LocalDate,
    val method: ContactMethod,
    val outcome: ContactOutcome,
    val reading: BpReading?,
    val dangerSigns: Set<DangerSign>,
    val medicineAvailability: ReportedMedicineAvailability,
    val sideEffectConcern: Boolean?,
    val nextDueDate: LocalDate?,
    val note: String?
)

data class FollowUpPlan(
    val contact: ChwContact,
    val updatedTasks: List<ChwFollowUpTask>,
    val newTask: ChwFollowUpTask?,
    val urgentReferral: Referral?
)

sealed interface FollowUpPlanResult {
  data class Planned(val plan: FollowUpPlan) : FollowUpPlanResult
  data class Rejected(val reason: FollowUpRejection) : FollowUpPlanResult
}

enum class FollowUpRejection {
  NOT_PERMITTED_FOR_ROLE,
  PATIENT_NOT_ASSIGNED_TO_THIS_CHW,
  INVALID_READING,
  NEXT_DUE_DATE_REQUIRED,
  NEXT_DUE_DATE_IN_PAST,
  NOTE_TOO_LONG
}

/**
 * Pure planning of a CHW follow-up contact.
 *
 * - Reached: the current task is completed and a next routine follow-up is scheduled.
 * - Not at home / phone unreachable: the task stays open and is rescheduled (attempt counted).
 * - Declined / transferred / moved: open tasks are cancelled; the coordinator/server decides
 *   any assignment change. Nothing here marks a patient "lost" (see FollowUpStatusClassifier).
 * - Danger signs or an urgent-range reading create an URGENT referral to the supervising clinic.
 */
class FollowUpPlanner(
    private val classifier: ScreeningClassifier,
    private val newId: () -> UUID
) {

  fun plan(
      request: FollowUpRequest,
      currentAssignments: List<PatientChwAssignment>,
      openTasksForPatient: List<ChwFollowUpTask>
  ): FollowUpPlanResult {
    if (!ChwAccessPolicy.isAllowed(request.role, ChwPermission.RECORD_CHW_FOLLOW_UP)) {
      return reject(FollowUpRejection.NOT_PERMITTED_FOR_ROLE)
    }
    if (currentAssignments.none { it.isCurrent && it.chwUserId == request.chwUserId }) {
      return reject(FollowUpRejection.PATIENT_NOT_ASSIGNED_TO_THIS_CHW)
    }
    if ((request.note?.length ?: 0) > MAX_NOTE_LENGTH) return reject(FollowUpRejection.NOTE_TOO_LONG)

    val needsNextDate = request.outcome.isSuccessful || request.outcome.isFailedAttempt
    if (needsNextDate && request.nextDueDate == null) return reject(FollowUpRejection.NEXT_DUE_DATE_REQUIRED)
    if (request.nextDueDate != null && request.nextDueDate.isBefore(request.today)) {
      return reject(FollowUpRejection.NEXT_DUE_DATE_IN_PAST)
    }

    val urgent = if (request.outcome.isSuccessful) {
      val classification = classifier.classify(ScreeningInput(
          first = request.reading,
          second = null,
          minutesBetweenReadings = null,
          dangerSigns = request.dangerSigns,
          specialPopulation = SpecialPopulation.NONE
      ))
      when (classification) {
        is ScreeningClassification.InvalidReading -> return reject(FollowUpRejection.INVALID_READING)
        is ScreeningClassification.Result -> classification.outcome == ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT
      }
    } else false

    val ts = Timestamps(request.occurredAt, request.occurredAt, null)
    val contactId = newId()
    val contact = ChwContact(
        id = contactId,
        patientId = request.patientId,
        chwUserId = request.chwUserId,
        taskId = request.taskId,
        occurredAt = request.occurredAt,
        method = request.method,
        outcome = request.outcome,
        systolic = request.reading?.systolic,
        diastolic = request.reading?.diastolic,
        dangerSigns = request.dangerSigns,
        medicineAvailability = request.medicineAvailability,
        sideEffectConcern = request.sideEffectConcern,
        nextDueDate = request.nextDueDate,
        note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        timestamps = ts,
        syncStatus = SyncStatus.PENDING
    )

    val myOpenTasks = openTasksForPatient.filter { it.chwUserId == request.chwUserId && it.status == FollowUpTaskStatus.OPEN }
    val targetTasks = if (request.taskId != null) myOpenTasks.filter { it.id == request.taskId } else myOpenTasks

    val updated: List<ChwFollowUpTask>
    var newTask: ChwFollowUpTask? = null

    when {
      request.outcome.isSuccessful -> {
        updated = targetTasks
            .filter { it.type != FollowUpTaskType.CHECK_CLINIC_CONFIRMATION || request.taskId == it.id }
            .map { it.copy(status = FollowUpTaskStatus.COMPLETED, completedByContactId = contactId, timestamps = it.timestamps.copy(updatedAt = request.occurredAt), syncStatus = SyncStatus.PENDING) }
        newTask = newTask(request, if (urgent) FollowUpTaskType.URGENT_REFERRAL_CHECK else FollowUpTaskType.ROUTINE_FOLLOW_UP,
            if (urgent) request.today else request.nextDueDate!!, ts)
      }
      request.outcome.isFailedAttempt -> {
        updated = targetTasks.map {
          it.copy(dueDate = request.nextDueDate!!, rescheduleCount = it.rescheduleCount + 1, timestamps = it.timestamps.copy(updatedAt = request.occurredAt), syncStatus = SyncStatus.PENDING)
        }
        if (targetTasks.isEmpty()) newTask = newTask(request, FollowUpTaskType.ROUTINE_FOLLOW_UP, request.nextDueDate!!, ts)
      }
      else -> {
        updated = myOpenTasks.map {
          it.copy(status = FollowUpTaskStatus.CANCELLED, completedByContactId = contactId, timestamps = it.timestamps.copy(updatedAt = request.occurredAt), syncStatus = SyncStatus.PENDING)
        }
      }
    }

    val referral = if (urgent) Referral(
        id = newId(),
        patientId = request.patientId,
        screeningEpisodeId = null,
        sourceUserId = request.chwUserId,
        originatingAreaId = request.communityAreaId,
        destinationFacilityId = request.supervisingFacilityId,
        priority = ReferralPriority.URGENT,
        reasonCodes = CodeSets.encode(listOf("chw_follow_up_urgent") + request.dangerSigns.filter { it != DangerSign.NONE }.map { it.code }),
        status = ReferralStatus.SUBMITTED_PENDING_SYNC,
        requestedAt = request.occurredAt,
        dueDate = request.today,
        acknowledgedAt = null,
        completedAt = null,
        clinicianUserId = null,
        outcomeCode = null,
        nextActionCode = null,
        timestamps = ts,
        syncStatus = SyncStatus.PENDING
    ) else null

    return FollowUpPlanResult.Planned(FollowUpPlan(contact, updated, newTask?.copy(referralId = referral?.id), referral))
  }

  private fun newTask(request: FollowUpRequest, type: FollowUpTaskType, due: LocalDate, ts: Timestamps) = ChwFollowUpTask(
      id = newId(),
      patientId = request.patientId,
      chwUserId = request.chwUserId,
      communityAreaId = request.communityAreaId,
      type = type,
      dueDate = due,
      status = FollowUpTaskStatus.OPEN,
      screeningEpisodeId = null,
      referralId = null,
      completedByContactId = null,
      rescheduleCount = 0,
      timestamps = ts,
      syncStatus = SyncStatus.PENDING
  )

  private fun reject(reason: FollowUpRejection) = FollowUpPlanResult.Rejected(reason)

  companion object {
    const val MAX_NOTE_LENGTH = 280
  }
}
