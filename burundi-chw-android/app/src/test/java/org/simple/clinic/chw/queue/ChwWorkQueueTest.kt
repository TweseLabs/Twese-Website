package org.simple.clinic.chw.queue

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.chw.ChwTestData
import org.simple.clinic.chw.ChwTestData.TODAY
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.referral.ReferralStatus
import java.util.UUID

class ChwWorkQueueTest {

  private val chw = UUID.randomUUID()

  @Test
  fun `tasks are bucketed into overdue, today, this week and later`() {
    val overdue = ChwTestData.task(UUID.randomUUID(), chw, dueDate = TODAY.minusDays(1))
    val today = ChwTestData.task(UUID.randomUUID(), chw, dueDate = TODAY)
    val thisWeek = ChwTestData.task(UUID.randomUUID(), chw, dueDate = TODAY.plusDays(6))
    val later = ChwTestData.task(UUID.randomUUID(), chw, dueDate = TODAY.plusDays(7))

    val queue = ChwWorkQueue.build(TODAY, listOf(later, thisWeek, today, overdue), emptyList())

    assertThat(queue[WorkQueueSection.OVERDUE]!!.single().taskId).isEqualTo(overdue.id)
    assertThat(queue[WorkQueueSection.DUE_TODAY]!!.single().taskId).isEqualTo(today.id)
    assertThat(queue[WorkQueueSection.DUE_THIS_WEEK]!!.single().taskId).isEqualTo(thisWeek.id)
    assertThat(queue[WorkQueueSection.ACTIVE_FOLLOW_UP]!!.single().taskId).isEqualTo(later.id)
  }

  @Test
  fun `urgent referrals and urgent checks are surfaced first`() {
    val patient = UUID.randomUUID()
    val queue = ChwWorkQueue.build(
        TODAY,
        listOf(ChwTestData.task(patient, chw, dueDate = TODAY.minusDays(3))),
        listOf(ChwTestData.referral(patient, priority = ReferralPriority.URGENT, dueDate = TODAY))
    )

    assertThat(queue.keys).containsExactly(WorkQueueSection.URGENT_UNRESOLVED)
  }

  @Test
  fun `each patient appears once in the most urgent applicable section`() {
    val patient = UUID.randomUUID()
    val queue = ChwWorkQueue.build(
        TODAY,
        listOf(
            ChwTestData.task(patient, chw, dueDate = TODAY.plusDays(20)),
            ChwTestData.task(patient, chw, dueDate = TODAY)
        ),
        emptyList()
    )

    assertThat(queue.values.flatten()).hasSize(1)
    assertThat(queue[WorkQueueSection.DUE_TODAY]).isNotNull()
  }

  @Test
  fun `routine open referrals show as awaiting clinical confirmation`() {
    val queue = ChwWorkQueue.build(TODAY, emptyList(), listOf(ChwTestData.referral(UUID.randomUUID())))

    assertThat(queue[WorkQueueSection.AWAITING_CLINICAL_CONFIRMATION]).hasSize(1)
  }

  @Test
  fun `closed referrals and completed tasks are excluded`() {
    val queue = ChwWorkQueue.build(
        TODAY,
        listOf(ChwTestData.task(UUID.randomUUID(), chw, status = FollowUpTaskStatus.COMPLETED)),
        listOf(ChwTestData.referral(UUID.randomUUID(), status = ReferralStatus.COMPLETED))
    )

    assertThat(queue).isEmpty()
  }

  @Test
  fun `urgent referral check tasks are urgent even if due in future`() {
    val queue = ChwWorkQueue.build(
        TODAY,
        listOf(ChwTestData.task(UUID.randomUUID(), chw, type = FollowUpTaskType.URGENT_REFERRAL_CHECK, dueDate = TODAY.plusDays(2))),
        emptyList()
    )

    assertThat(queue.keys).containsExactly(WorkQueueSection.URGENT_UNRESOLVED)
  }
}
