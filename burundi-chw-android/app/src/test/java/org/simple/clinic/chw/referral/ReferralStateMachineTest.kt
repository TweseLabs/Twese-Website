package org.simple.clinic.chw.referral

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.referral.ReferralStatus.ACCEPTED
import org.simple.clinic.chw.referral.ReferralStatus.CANCELLED
import org.simple.clinic.chw.referral.ReferralStatus.CLOSED_UNRESOLVED
import org.simple.clinic.chw.referral.ReferralStatus.COMPLETED
import org.simple.clinic.chw.referral.ReferralStatus.DRAFT
import org.simple.clinic.chw.referral.ReferralStatus.RECEIVED
import org.simple.clinic.chw.referral.ReferralStatus.SUBMITTED_PENDING_SYNC
import org.simple.clinic.chw.referral.ReferralStatus.UNABLE_TO_CONTACT

class ReferralStateMachineTest {

  private fun allowed(from: ReferralStatus, to: ReferralStatus, actor: ReferralActor, outcome: String? = "confirmed_htn") =
      ReferralStateMachine.transition(from, to, actor, outcome) is TransitionResult.Allowed

  @Test
  fun `CHW submits and can cancel before the clinic receives it`() {
    assertThat(allowed(DRAFT, SUBMITTED_PENDING_SYNC, ReferralActor.CHW)).isTrue()
    assertThat(allowed(SUBMITTED_PENDING_SYNC, CANCELLED, ReferralActor.CHW)).isTrue()
  }

  @Test
  fun `only the server can mark a referral received - never the device`() {
    ReferralActor.entries.filter { it != ReferralActor.SERVER }.forEach {
      assertThat(allowed(SUBMITTED_PENDING_SYNC, RECEIVED, it)).isFalse()
    }
    assertThat(allowed(SUBMITTED_PENDING_SYNC, RECEIVED, ReferralActor.SERVER)).isTrue()
  }

  @Test
  fun `CHW cannot accept, complete or close a referral`() {
    assertThat(allowed(RECEIVED, ACCEPTED, ReferralActor.CHW)).isFalse()
    assertThat(allowed(ACCEPTED, COMPLETED, ReferralActor.CHW)).isFalse()
    assertThat(allowed(ACCEPTED, CLOSED_UNRESOLVED, ReferralActor.CHW)).isFalse()
  }

  @Test
  fun `clinician accepts, records inability to contact, and completes with an outcome`() {
    assertThat(allowed(RECEIVED, ACCEPTED, ReferralActor.CLINICIAN)).isTrue()
    assertThat(allowed(ACCEPTED, UNABLE_TO_CONTACT, ReferralActor.CLINICIAN)).isTrue()
    assertThat(allowed(UNABLE_TO_CONTACT, ACCEPTED, ReferralActor.CLINICIAN)).isTrue()
    assertThat(allowed(ACCEPTED, COMPLETED, ReferralActor.CLINICIAN)).isTrue()
  }

  @Test
  fun `completion without a clinician outcome is denied`() {
    assertThat(ReferralStateMachine.transition(ACCEPTED, COMPLETED, ReferralActor.CLINICIAN, outcomeCode = null))
        .isInstanceOf(TransitionResult.Denied::class.java)
  }

  @Test
  fun `terminal states have no outgoing transitions`() {
    listOf(COMPLETED, CANCELLED, CLOSED_UNRESOLVED).forEach { terminal ->
      assertThat(terminal.isTerminal).isTrue()
      ReferralStatus.entries.forEach { to ->
        ReferralActor.entries.forEach { actor -> assertThat(allowed(terminal, to, actor)).isFalse() }
      }
    }
  }

  @Test
  fun `cannot skip from submitted straight to completed`() {
    assertThat(allowed(SUBMITTED_PENDING_SYNC, COMPLETED, ReferralActor.CLINICIAN)).isFalse()
  }

  @Test
  fun `device-only statuses are not shown as received by clinic`() {
    assertThat(DRAFT.isReceivedByClinic).isFalse()
    assertThat(SUBMITTED_PENDING_SYNC.isReceivedByClinic).isFalse()
    assertThat(RECEIVED.isReceivedByClinic).isTrue()
  }

  @Test
  fun `legacy clinic users and admins have no referral actor`() {
    assertThat(ReferralStateMachine.actorFor(ChwRole.LEGACY_CLINIC_USER)).isNull()
    assertThat(ReferralStateMachine.actorFor(ChwRole.PROGRAM_ADMIN)).isNull()
    assertThat(ReferralStateMachine.actorFor(ChwRole.CHW)).isEqualTo(ReferralActor.CHW)
  }
}
