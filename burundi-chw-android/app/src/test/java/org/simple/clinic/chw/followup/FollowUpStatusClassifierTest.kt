package org.simple.clinic.chw.followup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.chw.ContactOutcome
import java.time.Instant
import java.time.ZoneOffset

class FollowUpStatusClassifierTest {

  private val now = Instant.parse("2026-06-30T10:00:00Z")
  private val policy = LostToFollowUpPolicy(minimumFailedAttempts = 3, minimumDaysWithoutContact = 90, minimumDistinctAttemptDays = 3)

  private fun daysAgo(d: Long) = now.minusSeconds(d * 86_400)

  private fun classify(
      contacts: List<ContactAttempt>,
      clinicOverdue: Boolean = false,
      chwOverdue: Boolean = false
  ) = FollowUpStatusClassifier.classify(contacts, clinicOverdue, chwOverdue, now, { it.atZone(ZoneOffset.UTC).toLocalDate() }, policy)

  @Test
  fun `a single missed clinic visit is a missed clinic visit, not lost to follow-up`() {
    assertThat(classify(listOf(ContactAttempt(daysAgo(10), ContactOutcome.REACHED)), clinicOverdue = true))
        .isEqualTo(FollowUpStatus.MISSED_CLINIC_VISIT)
  }

  @Test
  fun `overdue CHW task is a missed CHW contact`() {
    assertThat(classify(emptyList(), chwOverdue = true)).isEqualTo(FollowUpStatus.MISSED_CHW_CONTACT)
  }

  @Test
  fun `one failed attempt is unreachable, not lost`() {
    assertThat(classify(listOf(ContactAttempt(daysAgo(200), ContactOutcome.NOT_AT_HOME)))).isEqualTo(FollowUpStatus.UNREACHABLE)
  }

  @Test
  fun `many failed attempts on the same day are not enough to be lost`() {
    val sameDay = (1..5).map { ContactAttempt(daysAgo(120).plusSeconds(it * 60L), ContactOutcome.PHONE_UNREACHABLE) }

    assertThat(classify(sameDay)).isEqualTo(FollowUpStatus.UNREACHABLE)
  }

  @Test
  fun `lost only after enough failed attempts on distinct days over enough time`() {
    val attempts = listOf(
        ContactAttempt(daysAgo(120), ContactOutcome.REACHED),
        ContactAttempt(daysAgo(100), ContactOutcome.NOT_AT_HOME),
        ContactAttempt(daysAgo(60), ContactOutcome.PHONE_UNREACHABLE),
        ContactAttempt(daysAgo(30), ContactOutcome.NOT_AT_HOME)
    )

    assertThat(classify(attempts)).isEqualTo(FollowUpStatus.LOST_TO_FOLLOW_UP)
  }

  @Test
  fun `a successful contact resets failed attempts`() {
    val attempts = listOf(
        ContactAttempt(daysAgo(150), ContactOutcome.NOT_AT_HOME),
        ContactAttempt(daysAgo(140), ContactOutcome.NOT_AT_HOME),
        ContactAttempt(daysAgo(130), ContactOutcome.NOT_AT_HOME),
        ContactAttempt(daysAgo(5), ContactOutcome.REACHED)
    )

    assertThat(classify(attempts)).isEqualTo(FollowUpStatus.ACTIVE)
  }

  @Test
  fun `declined and transferred are explicit states, distinct from lost`() {
    assertThat(classify(listOf(ContactAttempt(daysAgo(1), ContactOutcome.DECLINED)))).isEqualTo(FollowUpStatus.DECLINED)
    assertThat(classify(listOf(ContactAttempt(daysAgo(1), ContactOutcome.TRANSFERRED_OUT)))).isEqualTo(FollowUpStatus.TRANSFERRED)
    assertThat(classify(listOf(ContactAttempt(daysAgo(1), ContactOutcome.MOVED_AWAY)))).isEqualTo(FollowUpStatus.TRANSFERRED)
  }
}
