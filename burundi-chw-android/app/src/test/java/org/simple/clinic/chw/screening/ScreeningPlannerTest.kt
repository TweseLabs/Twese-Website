package org.simple.clinic.chw.screening

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.chw.AssignmentStatus
import org.simple.clinic.chw.ChwTestData
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.referral.ReferralStatus
import org.simple.clinic.patient.SyncStatus
import java.util.UUID

class ScreeningPlannerTest {

  private val patientId = UUID.fromString("00000000-0000-0000-0000-00000000aaaa")
  private val chwId = UUID.fromString("00000000-0000-0000-0000-00000000cccc")
  private val clinicId = UUID.fromString("00000000-0000-0000-0000-00000000ffff")
  private val planner = ScreeningPlanner(ScreeningClassifier(ScreeningProtocol.DRAFT_UNAPPROVED), ChwTestData.sequentialIds())

  private fun request(
      first: BpReading? = BpReading(120, 80),
      second: BpReading? = BpReading(122, 78),
      signs: Set<DangerSign> = setOf(DangerSign.NONE),
      role: ChwRole = ChwRole.CHW,
      consent: Boolean = true
  ) = ScreeningRequest(
      patientId = patientId,
      chwUserId = chwId,
      role = role,
      communityAreaId = null,
      supervisingFacilityId = clinicId,
      occurredAt = ChwTestData.NOW,
      today = ChwTestData.TODAY,
      locationType = ScreeningLocationType.HOUSEHOLD,
      consentVersion = "consent-draft-1",
      consentGiven = consent,
      input = ScreeningInput(first, second, 2, signs, SpecialPopulation.NONE)
  )

  @Test
  fun `normal screening of a new patient creates episode, pending assignment and rescreen task but no referral`() {
    val result = planner.plan(request(), currentAssignments = emptyList(), openTasksForPatient = emptyList()) as PlanResult.Planned
    val plan = result.plan

    assertThat(plan.episode.outcome).isEqualTo(ScreeningOutcome.NORMAL_SCREENING)
    assertThat(plan.episode.syncStatus).isEqualTo(SyncStatus.PENDING)
    assertThat(plan.referral).isNull()
    assertThat(plan.newTask.type).isEqualTo(FollowUpTaskType.RESCREEN)
    assertThat(plan.newTask.dueDate).isEqualTo(ChwTestData.TODAY.plusDays(365))
    assertThat(plan.newPatientAssignment!!.status).isEqualTo(AssignmentStatus.PENDING_SERVER_CONFIRMATION)
    assertThat(plan.newPatientAssignment!!.chwUserId).isEqualTo(chwId)
  }

  @Test
  fun `urgent screening creates an urgent referral to the supervising clinic due today and an urgent check task`() {
    val plan = (planner.plan(request(signs = setOf(DangerSign.CHEST_PAIN)), emptyList(), emptyList()) as PlanResult.Planned).plan

    val referral = plan.referral!!
    assertThat(referral.priority).isEqualTo(ReferralPriority.URGENT)
    assertThat(referral.destinationFacilityId).isEqualTo(clinicId)
    assertThat(referral.status).isEqualTo(ReferralStatus.SUBMITTED_PENDING_SYNC)
    assertThat(referral.status.isReceivedByClinic).isFalse()
    assertThat(referral.dueDate).isEqualTo(ChwTestData.TODAY)
    assertThat(referral.screeningEpisodeId).isEqualTo(plan.episode.id)
    assertThat(plan.episode.referralId).isEqualTo(referral.id)
    assertThat(plan.newTask.type).isEqualTo(FollowUpTaskType.URGENT_REFERRAL_CHECK)
    assertThat(plan.newTask.referralId).isEqualTo(referral.id)
  }

  @Test
  fun `confirmation required creates routine referral and a task to check clinic confirmation`() {
    val plan = (planner.plan(request(first = BpReading(150, 95), second = BpReading(148, 94)), emptyList(), emptyList()) as PlanResult.Planned).plan

    assertThat(plan.episode.outcome).isEqualTo(ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED)
    assertThat(plan.referral!!.priority).isEqualTo(ReferralPriority.ROUTINE)
    assertThat(plan.newTask.type).isEqualTo(FollowUpTaskType.CHECK_CLINIC_CONFIRMATION)
  }

  @Test
  fun `re-screening an already assigned patient does not create a second assignment and completes open repeat tasks`() {
    val existingRepeat = ChwTestData.task(patientId, chwId, type = FollowUpTaskType.REPEAT_SCREENING)
    val unrelatedFollowUp = ChwTestData.task(patientId, chwId, type = FollowUpTaskType.ROUTINE_FOLLOW_UP)

    val plan = (planner.plan(
        request(),
        currentAssignments = listOf(ChwTestData.assignment(patientId, chwId)),
        openTasksForPatient = listOf(existingRepeat, unrelatedFollowUp)
    ) as PlanResult.Planned).plan

    assertThat(plan.newPatientAssignment).isNull()
    assertThat(plan.tasksToComplete.map { it.id }).containsExactly(existingRepeat.id)
    assertThat(plan.tasksToComplete.single().status).isEqualTo(FollowUpTaskStatus.COMPLETED)
    assertThat(plan.tasksToComplete.single().syncStatus).isEqualTo(SyncStatus.PENDING)
  }

  @Test
  fun `patient actively assigned to another CHW is rejected`() {
    val result = planner.plan(request(), listOf(ChwTestData.assignment(patientId, UUID.randomUUID())), emptyList())

    assertThat(result).isEqualTo(PlanResult.Rejected(RejectionReason.PATIENT_ASSIGNED_TO_ANOTHER_CHW))
  }

  @Test
  fun `ended assignment of another CHW does not block screening`() {
    val ended = ChwTestData.assignment(patientId, UUID.randomUUID(), status = AssignmentStatus.ENDED)

    assertThat(planner.plan(request(), listOf(ended), emptyList())).isInstanceOf(PlanResult.Planned::class.java)
  }

  @Test
  fun `screening without recorded consent is rejected`() {
    assertThat(planner.plan(request(consent = false), emptyList(), emptyList()))
        .isEqualTo(PlanResult.Rejected(RejectionReason.CONSENT_NOT_RECORDED))
  }

  @Test
  fun `roles without screening permission are rejected`() {
    listOf(ChwRole.PROGRAM_ADMIN, ChwRole.FIELD_COORDINATOR, ChwRole.LEGACY_CLINIC_USER, ChwRole.CLINICIAN).forEach { role ->
      assertThat(planner.plan(request(role = role), emptyList(), emptyList()))
          .isEqualTo(PlanResult.Rejected(RejectionReason.NOT_PERMITTED_FOR_ROLE))
    }
  }

  @Test
  fun `implausible readings are rejected and nothing is planned`() {
    assertThat(planner.plan(request(first = BpReading(80, 120)), emptyList(), emptyList()))
        .isEqualTo(PlanResult.Rejected(RejectionReason.INVALID_READING))
  }

  @Test
  fun `episode records the rule version and that it is not approved`() {
    val plan = (planner.plan(request(), emptyList(), emptyList()) as PlanResult.Planned).plan

    assertThat(plan.episode.decisionRuleVersion).isEqualTo(ScreeningProtocol.DRAFT_UNAPPROVED.version)
    assertThat(plan.episode.decisionRuleApproved).isFalse()
  }

  @Test
  fun `all generated ids are unique`() {
    val plan = (planner.plan(request(signs = setOf(DangerSign.CHEST_PAIN)), emptyList(), emptyList()) as PlanResult.Planned).plan
    val ids = listOf(plan.episode.id, plan.referral!!.id, plan.newTask.id, plan.newPatientAssignment!!.id)

    assertThat(ids.toSet()).hasSize(ids.size)
  }
}
