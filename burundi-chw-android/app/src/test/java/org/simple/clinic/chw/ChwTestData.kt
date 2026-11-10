package org.simple.clinic.chw

import org.simple.clinic.chw.area.PatientChwAssignment
import org.simple.clinic.chw.followup.ChwFollowUpTask
import org.simple.clinic.chw.referral.Referral
import org.simple.clinic.chw.referral.ReferralStatus
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Synthetic test data only. */
object ChwTestData {

  val NOW: Instant = Instant.parse("2026-03-10T08:00:00Z")
  val TODAY: LocalDate = LocalDate.parse("2026-03-10")

  fun sequentialIds(): () -> UUID {
    var n = 0L
    return { UUID(0L, ++n) }
  }

  fun assignment(
      patientId: UUID,
      chwUserId: UUID,
      status: AssignmentStatus = AssignmentStatus.ACTIVE
  ) = PatientChwAssignment(
      id = UUID.randomUUID(),
      patientId = patientId,
      chwUserId = chwUserId,
      communityAreaId = null,
      assignedAt = NOW.minusSeconds(86_400),
      status = status,
      endReason = null,
      previousAssignmentId = null,
      timestamps = Timestamps(NOW, NOW, null),
      syncStatus = SyncStatus.DONE
  )

  fun task(
      patientId: UUID,
      chwUserId: UUID,
      type: FollowUpTaskType = FollowUpTaskType.ROUTINE_FOLLOW_UP,
      dueDate: LocalDate = TODAY,
      status: FollowUpTaskStatus = FollowUpTaskStatus.OPEN,
      id: UUID = UUID.randomUUID()
  ) = ChwFollowUpTask(
      id = id,
      patientId = patientId,
      chwUserId = chwUserId,
      communityAreaId = null,
      type = type,
      dueDate = dueDate,
      status = status,
      screeningEpisodeId = null,
      referralId = null,
      completedByContactId = null,
      rescheduleCount = 0,
      timestamps = Timestamps(NOW, NOW, null),
      syncStatus = SyncStatus.DONE
  )

  fun referral(
      patientId: UUID,
      priority: ReferralPriority = ReferralPriority.ROUTINE,
      status: ReferralStatus = ReferralStatus.SUBMITTED_PENDING_SYNC,
      dueDate: LocalDate = TODAY.plusDays(14)
  ) = Referral(
      id = UUID.randomUUID(),
      patientId = patientId,
      screeningEpisodeId = null,
      sourceUserId = UUID.randomUUID(),
      originatingAreaId = null,
      destinationFacilityId = UUID.randomUUID(),
      priority = priority,
      reasonCodes = "",
      status = status,
      requestedAt = NOW,
      dueDate = dueDate,
      acknowledgedAt = null,
      completedAt = null,
      clinicianUserId = null,
      outcomeCode = null,
      nextActionCode = null,
      timestamps = Timestamps(NOW, NOW, null),
      syncStatus = SyncStatus.PENDING
  )
}
