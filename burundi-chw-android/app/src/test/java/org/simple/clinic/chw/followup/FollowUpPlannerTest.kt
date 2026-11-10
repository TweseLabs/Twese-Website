package org.simple.clinic.chw.followup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.chw.ChwTestData
import org.simple.clinic.chw.ContactMethod
import org.simple.clinic.chw.ContactOutcome
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.ReportedMedicineAvailability
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.screening.BpReading
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningClassifier
import org.simple.clinic.chw.screening.ScreeningProtocol
import java.time.LocalDate
import java.util.UUID

class FollowUpPlannerTest {

  private val patientId = UUID.randomUUID()
  private val chwId = UUID.randomUUID()
  private val planner = FollowUpPlanner(ScreeningClassifier(ScreeningProtocol.DRAFT_UNAPPROVED), ChwTestData.sequentialIds())
  private val assigned = listOf(ChwTestData.assignment(patientId, chwId))

  private fun request(
      outcome: ContactOutcome = ContactOutcome.REACHED,
      reading: BpReading? = BpReading(130, 84),
      signs: Set<DangerSign> = setOf(DangerSign.NONE),
      next: LocalDate? = ChwTestData.TODAY.plusDays(30),
      taskId: UUID? = null,
      role: ChwRole = ChwRole.CHW,
      note: String? = null
  ) = FollowUpRequest(
      patientId = patientId,
      chwUserId = chwId,
      role = role,
      communityAreaId = null,
      supervisingFacilityId = UUID.randomUUID(),
      taskId = taskId,
      occurredAt = ChwTestData.NOW,
      today = ChwTestData.TODAY,
      method = ContactMethod.HOME_VISIT,
      outcome = outcome,
      reading = reading,
      dangerSigns = signs,
      medicineAvailability = ReportedMedicineAvailability.RAN_OUT,
      sideEffectConcern = false,
      nextDueDate = next,
      note = note
  )

  private fun planned(result: FollowUpPlanResult) = (result as FollowUpPlanResult.Planned).plan

  @Test
  fun `reached contact completes the due task and schedules the next follow-up`() {
    val task = ChwTestData.task(patientId, chwId)
    val plan = planned(planner.plan(request(taskId = task.id), assigned, listOf(task)))

    assertThat(plan.updatedTasks.single().status).isEqualTo(FollowUpTaskStatus.COMPLETED)
    assertThat(plan.updatedTasks.single().completedByContactId).isEqualTo(plan.contact.id)
    assertThat(plan.newTask!!.dueDate).isEqualTo(ChwTestData.TODAY.plusDays(30))
    assertThat(plan.newTask!!.type).isEqualTo(FollowUpTaskType.ROUTINE_FOLLOW_UP)
    assertThat(plan.urgentReferral).isNull()
  }

  @Test
  fun `medicine availability is stored as patient-reported, not as a prescription`() {
    val plan = planned(planner.plan(request(), assigned, emptyList()))

    assertThat(plan.contact.medicineAvailability).isEqualTo(ReportedMedicineAvailability.RAN_OUT)
  }

  @Test
  fun `not at home keeps the task open and reschedules it - it is never marked lost`() {
    val task = ChwTestData.task(patientId, chwId)
    val plan = planned(planner.plan(request(outcome = ContactOutcome.NOT_AT_HOME, reading = null, next = ChwTestData.TODAY.plusDays(3), taskId = task.id), assigned, listOf(task)))

    val updated = plan.updatedTasks.single()
    assertThat(updated.status).isEqualTo(FollowUpTaskStatus.OPEN)
    assertThat(updated.dueDate).isEqualTo(ChwTestData.TODAY.plusDays(3))
    assertThat(updated.rescheduleCount).isEqualTo(1)
    assertThat(plan.newTask).isNull()
  }

  @Test
  fun `declined cancels open tasks and schedules nothing`() {
    val tasks = listOf(ChwTestData.task(patientId, chwId), ChwTestData.task(patientId, chwId, type = FollowUpTaskType.RESCREEN))
    val plan = planned(planner.plan(request(outcome = ContactOutcome.DECLINED, reading = null, next = null), assigned, tasks))

    assertThat(plan.updatedTasks.map { it.status }.toSet()).containsExactly(FollowUpTaskStatus.CANCELLED)
    assertThat(plan.newTask).isNull()
  }

  @Test
  fun `danger sign at follow-up creates an urgent referral due today`() {
    val plan = planned(planner.plan(request(signs = setOf(DangerSign.SHORTNESS_OF_BREATH)), assigned, emptyList()))

    assertThat(plan.urgentReferral!!.priority).isEqualTo(ReferralPriority.URGENT)
    assertThat(plan.urgentReferral!!.dueDate).isEqualTo(ChwTestData.TODAY)
    assertThat(plan.newTask!!.type).isEqualTo(FollowUpTaskType.URGENT_REFERRAL_CHECK)
    assertThat(plan.newTask!!.referralId).isEqualTo(plan.urgentReferral!!.id)
  }

  @Test
  fun `urgent range reading at follow-up creates an urgent referral`() {
    val plan = planned(planner.plan(request(reading = BpReading(190, 115)), assigned, emptyList()))

    assertThat(plan.urgentReferral).isNotNull()
  }

  @Test
  fun `patient not assigned to this CHW is rejected`() {
    val otherChw = listOf(ChwTestData.assignment(patientId, UUID.randomUUID()))

    assertThat(planner.plan(request(), otherChw, emptyList()))
        .isEqualTo(FollowUpPlanResult.Rejected(FollowUpRejection.PATIENT_NOT_ASSIGNED_TO_THIS_CHW))
  }

  @Test
  fun `clinician role cannot record a CHW follow-up`() {
    assertThat(planner.plan(request(role = ChwRole.CLINICIAN), assigned, emptyList()))
        .isEqualTo(FollowUpPlanResult.Rejected(FollowUpRejection.NOT_PERMITTED_FOR_ROLE))
  }

  @Test
  fun `next due date is required after reaching or failing to reach the patient`() {
    assertThat(planner.plan(request(next = null), assigned, emptyList()))
        .isEqualTo(FollowUpPlanResult.Rejected(FollowUpRejection.NEXT_DUE_DATE_REQUIRED))
  }

  @Test
  fun `next due date in the past is rejected`() {
    assertThat(planner.plan(request(next = ChwTestData.TODAY.minusDays(1)), assigned, emptyList()))
        .isEqualTo(FollowUpPlanResult.Rejected(FollowUpRejection.NEXT_DUE_DATE_IN_PAST))
  }

  @Test
  fun `implausible follow-up reading is rejected`() {
    assertThat(planner.plan(request(reading = BpReading(20, 10)), assigned, emptyList()))
        .isEqualTo(FollowUpPlanResult.Rejected(FollowUpRejection.INVALID_READING))
  }

  @Test
  fun `overlong notes are rejected and blank notes are dropped`() {
    assertThat(planner.plan(request(note = "x".repeat(FollowUpPlanner.MAX_NOTE_LENGTH + 1)), assigned, emptyList()))
        .isEqualTo(FollowUpPlanResult.Rejected(FollowUpRejection.NOTE_TOO_LONG))
    assertThat(planned(planner.plan(request(note = "   "), assigned, emptyList())).contact.note).isNull()
  }
}
