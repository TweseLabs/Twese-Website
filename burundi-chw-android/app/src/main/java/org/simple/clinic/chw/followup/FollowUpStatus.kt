package org.simple.clinic.chw.followup

import org.simple.clinic.chw.ContactOutcome
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Engagement status of a patient in community follow-up. These categories are intentionally
 * distinct so that, for example, one missed clinic visit is never reported as "lost".
 */
enum class FollowUpStatus {
  ACTIVE,
  MISSED_CLINIC_VISIT,
  MISSED_CHW_CONTACT,
  UNREACHABLE,
  DECLINED,
  TRANSFERRED,
  LOST_TO_FOLLOW_UP
}

/**
 * Policy values (require programme approval, see docs/burundi/clinical-rules-to-approve.md).
 */
data class LostToFollowUpPolicy(
    /** Minimum consecutive failed contact attempts since the last successful contact. */
    val minimumFailedAttempts: Int,
    /** Minimum time since the last successful contact (or first attempt if never reached). */
    val minimumDaysWithoutContact: Long,
    /** Failed attempts must be spread over at least this many distinct days. */
    val minimumDistinctAttemptDays: Int
) {
  companion object {
    val DRAFT_UNAPPROVED = LostToFollowUpPolicy(
        minimumFailedAttempts = 3,
        minimumDaysWithoutContact = 90,
        minimumDistinctAttemptDays = 3
    )
  }
}

data class ContactAttempt(val occurredAt: Instant, val outcome: ContactOutcome)

object FollowUpStatusClassifier {

  /**
   * @param contacts all CHW contact attempts for the patient, any order.
   * @param hasOverdueClinicAppointment whether the upstream clinic appointment is overdue.
   * @param hasOverdueChwTask whether a CHW task is past its due date.
   */
  fun classify(
      contacts: List<ContactAttempt>,
      hasOverdueClinicAppointment: Boolean,
      hasOverdueChwTask: Boolean,
      now: Instant,
      zoneDate: (Instant) -> LocalDate,
      policy: LostToFollowUpPolicy
  ): FollowUpStatus {
    val ordered = contacts.sortedBy { it.occurredAt }
    val latest = ordered.lastOrNull()

    // Explicit, patient- or clinic-reported states take precedence and are never inferred.
    when (latest?.outcome) {
      ContactOutcome.TRANSFERRED_OUT, ContactOutcome.MOVED_AWAY -> return FollowUpStatus.TRANSFERRED
      ContactOutcome.DECLINED -> return FollowUpStatus.DECLINED
      else -> Unit
    }

    val lastSuccess = ordered.lastOrNull { it.outcome.isSuccessful }
    val failedSinceSuccess = ordered
        .filter { lastSuccess == null || it.occurredAt.isAfter(lastSuccess.occurredAt) }
        .filter { it.outcome.isFailedAttempt }

    if (failedSinceSuccess.isNotEmpty()) {
      val reference = lastSuccess?.occurredAt ?: failedSinceSuccess.first().occurredAt
      val daysWithoutContact = Duration.between(reference, now).toDays()
      val distinctDays = failedSinceSuccess.map { zoneDate(it.occurredAt) }.distinct().size

      val meetsLostCriteria = failedSinceSuccess.size >= policy.minimumFailedAttempts &&
          daysWithoutContact >= policy.minimumDaysWithoutContact &&
          distinctDays >= policy.minimumDistinctAttemptDays

      return if (meetsLostCriteria) FollowUpStatus.LOST_TO_FOLLOW_UP else FollowUpStatus.UNREACHABLE
    }

    return when {
      hasOverdueChwTask -> FollowUpStatus.MISSED_CHW_CONTACT
      hasOverdueClinicAppointment -> FollowUpStatus.MISSED_CLINIC_VISIT
      else -> FollowUpStatus.ACTIVE
    }
  }
}
