package org.simple.clinic.chw.queue

import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.followup.ChwFollowUpTask
import org.simple.clinic.chw.referral.Referral
import org.simple.clinic.chw.referral.ReferralStatus
import java.time.LocalDate
import java.util.UUID

enum class WorkQueueSection {
  URGENT_UNRESOLVED,
  OVERDUE,
  DUE_TODAY,
  DUE_THIS_WEEK,
  AWAITING_CLINICAL_CONFIRMATION,
  ACTIVE_FOLLOW_UP
}

data class WorkQueueItem(
    val patientId: UUID,
    val section: WorkQueueSection,
    val dueDate: LocalDate,
    val taskId: UUID?,
    val referralId: UUID?,
    val taskType: FollowUpTaskType?,
    val referralStatus: ReferralStatus?
)

/**
 * Builds the CHW home work queue from local tasks and referrals.
 *
 * Each patient appears once, in the most urgent section that applies, so counts reflect patients
 * needing action, not raw record counts. Pure function; unit tested.
 */
object ChwWorkQueue {

  fun build(
      today: LocalDate,
      openTasks: List<ChwFollowUpTask>,
      referrals: List<Referral>
  ): Map<WorkQueueSection, List<WorkQueueItem>> {
    val endOfWeek = today.plusDays(6)
    val candidates = mutableListOf<WorkQueueItem>()

    referrals
        .filter { !it.status.isTerminal && it.timestamps.deletedAt == null }
        .forEach { referral ->
          val section = when {
            referral.priority == ReferralPriority.URGENT -> WorkQueueSection.URGENT_UNRESOLVED
            else -> WorkQueueSection.AWAITING_CLINICAL_CONFIRMATION
          }
          candidates += WorkQueueItem(
              patientId = referral.patientId,
              section = section,
              dueDate = referral.dueDate,
              taskId = null,
              referralId = referral.id,
              taskType = null,
              referralStatus = referral.status
          )
        }

    openTasks
        .filter { it.status == FollowUpTaskStatus.OPEN && it.timestamps.deletedAt == null }
        .forEach { task ->
          val section = when {
            task.type == FollowUpTaskType.URGENT_REFERRAL_CHECK -> WorkQueueSection.URGENT_UNRESOLVED
            task.dueDate.isBefore(today) -> WorkQueueSection.OVERDUE
            task.dueDate == today -> WorkQueueSection.DUE_TODAY
            !task.dueDate.isAfter(endOfWeek) -> WorkQueueSection.DUE_THIS_WEEK
            else -> WorkQueueSection.ACTIVE_FOLLOW_UP
          }
          candidates += WorkQueueItem(
              patientId = task.patientId,
              section = section,
              dueDate = task.dueDate,
              taskId = task.id,
              referralId = task.referralId,
              taskType = task.type,
              referralStatus = null
          )
        }

    return candidates
        .groupBy { it.patientId }
        .values
        .map { itemsForPatient -> itemsForPatient.minWith(compareBy({ it.section.ordinal }, { it.dueDate })) }
        .groupBy { it.section }
        .mapValues { (_, items) -> items.sortedBy { it.dueDate } }
  }
}
