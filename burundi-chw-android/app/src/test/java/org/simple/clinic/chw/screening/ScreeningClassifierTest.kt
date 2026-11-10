package org.simple.clinic.chw.screening

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.chw.screening.ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED
import org.simple.clinic.chw.screening.ScreeningOutcome.NEEDS_CLINICIAN_REVIEW
import org.simple.clinic.chw.screening.ScreeningOutcome.NORMAL_SCREENING
import org.simple.clinic.chw.screening.ScreeningOutcome.REPEAT_REQUIRED
import org.simple.clinic.chw.screening.ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT

/**
 * Uses an explicit TEST fixture protocol (not the draft) so these tests document rule ORDER and
 * BEHAVIOUR independently of whatever thresholds clinicians eventually approve.
 */
class ScreeningClassifierTest {

  private val fixture = ScreeningProtocol.DRAFT_UNAPPROVED.copy(
      version = "test-fixture-1",
      elevatedSystolic = 140,
      elevatedDiastolic = 90,
      urgentSystolic = 180,
      urgentDiastolic = 110,
      maxSystolicDifferenceBetweenReadings = 25,
      minimumMinutesBetweenReadings = 1
  )
  private val classifier = ScreeningClassifier(fixture)

  private fun input(
      first: BpReading? = BpReading(120, 80),
      second: BpReading? = BpReading(118, 78),
      minutes: Long? = 2,
      signs: Set<DangerSign> = setOf(DangerSign.NONE),
      population: SpecialPopulation = SpecialPopulation.NONE
  ) = ScreeningInput(first, second, minutes, signs, population)

  private fun outcomeOf(input: ScreeningInput): ScreeningOutcome =
      (classifier.classify(input) as ScreeningClassification.Result).outcome

  @Test
  fun `two normal readings without danger signs is a normal screening, not a diagnosis`() {
    val result = classifier.classify(input()) as ScreeningClassification.Result

    assertThat(result.outcome).isEqualTo(NORMAL_SCREENING)
    assertThat(result.protocolVersion).isEqualTo("test-fixture-1")
    assertThat(result.protocolApproved).isFalse()
  }

  @Test
  fun `a single elevated reading is never enough - repeat is required`() {
    assertThat(outcomeOf(input(first = BpReading(150, 95), second = null))).isEqualTo(REPEAT_REQUIRED)
  }

  @Test
  fun `a single normal reading is a normal screening`() {
    assertThat(outcomeOf(input(second = null))).isEqualTo(NORMAL_SCREENING)
  }

  @Test
  fun `average of two readings at or above threshold requires clinical confirmation`() {
    assertThat(outcomeOf(input(first = BpReading(146, 92), second = BpReading(142, 90)))).isEqualTo(CLINICAL_CONFIRMATION_REQUIRED)
  }

  @Test
  fun `elevated first reading with normal second reading uses the average`() {
    // average 135/85 -> below threshold
    assertThat(outcomeOf(input(first = BpReading(142, 88), second = BpReading(128, 82)))).isEqualTo(NORMAL_SCREENING)
  }

  @Test
  fun `diastolic alone at threshold counts as elevated`() {
    assertThat(outcomeOf(input(first = BpReading(130, 92), second = BpReading(130, 90)))).isEqualTo(CLINICAL_CONFIRMATION_REQUIRED)
  }

  @Test
  fun `any reading at the urgent threshold is urgent even with only one reading`() {
    assertThat(outcomeOf(input(first = BpReading(182, 100), second = null))).isEqualTo(URGENT_CLINICAL_ASSESSMENT)
    assertThat(outcomeOf(input(first = BpReading(150, 112), second = null))).isEqualTo(URGENT_CLINICAL_ASSESSMENT)
  }

  @Test
  fun `a danger sign is urgent regardless of a normal blood pressure`() {
    val result = classifier.classify(input(signs = setOf(DangerSign.CHEST_PAIN))) as ScreeningClassification.Result

    assertThat(result.outcome).isEqualTo(URGENT_CLINICAL_ASSESSMENT)
    assertThat(result.reasons).contains(ScreeningReason.DANGER_SIGN_REPORTED)
  }

  @Test
  fun `a reported symptom the protocol does not list is not ignored - it goes to clinician review`() {
    val protocol = fixture.copy(dangerSignCodes = listOf(DangerSign.CHEST_PAIN.code))
    val result = ScreeningClassifier(protocol).classify(input(signs = setOf(DangerSign.VISION_CHANGES))) as ScreeningClassification.Result

    assertThat(result.outcome).isEqualTo(NEEDS_CLINICIAN_REVIEW)
    assertThat(result.reasons).containsExactly(ScreeningReason.SYMPTOM_NOT_COVERED_BY_PROTOCOL)
  }

  @Test
  fun `danger sign takes precedence over special population`() {
    assertThat(outcomeOf(input(signs = setOf(DangerSign.SEVERE_HEADACHE), population = SpecialPopulation.PREGNANT_OR_RECENTLY_DELIVERED)))
        .isEqualTo(URGENT_CLINICAL_ASSESSMENT)
  }

  @Test
  fun `pregnancy or unknown population routes to clinician review`() {
    assertThat(outcomeOf(input(population = SpecialPopulation.PREGNANT_OR_RECENTLY_DELIVERED))).isEqualTo(NEEDS_CLINICIAN_REVIEW)
    assertThat(outcomeOf(input(population = SpecialPopulation.UNKNOWN))).isEqualTo(NEEDS_CLINICIAN_REVIEW)
  }

  @Test
  fun `an incomplete symptom checklist routes to clinician review`() {
    val result = classifier.classify(input(signs = emptySet())) as ScreeningClassification.Result

    assertThat(result.outcome).isEqualTo(NEEDS_CLINICIAN_REVIEW)
    assertThat(result.reasons).containsExactly(ScreeningReason.SYMPTOM_CHECKLIST_NOT_COMPLETED)
  }

  @Test
  fun `no reading routes to clinician review`() {
    assertThat(outcomeOf(input(first = null, second = null))).isEqualTo(NEEDS_CLINICIAN_REVIEW)
  }

  @Test
  fun `readings taken too close together must be repeated`() {
    assertThat(outcomeOf(input(first = BpReading(150, 95), second = BpReading(148, 94), minutes = 0))).isEqualTo(REPEAT_REQUIRED)
  }

  @Test
  fun `widely inconsistent readings must be repeated`() {
    val result = classifier.classify(input(first = BpReading(170, 95), second = BpReading(130, 85))) as ScreeningClassification.Result

    assertThat(result.outcome).isEqualTo(REPEAT_REQUIRED)
    assertThat(result.reasons).containsExactly(ScreeningReason.READINGS_INCONSISTENT)
  }

  @Test
  fun `implausible readings are rejected instead of classified`() {
    val result = classifier.classify(input(first = BpReading(400, 80), second = BpReading(120, 130)))

    assertThat(result).isEqualTo(ScreeningClassification.InvalidReading(listOf(
        ReadingProblem.FIRST_SYSTOLIC_OUT_OF_RANGE,
        ReadingProblem.SECOND_DIASTOLIC_NOT_BELOW_SYSTOLIC
    )))
  }

  @Test
  fun `when second reading is optional by protocol a single elevated reading goes to clinical confirmation`() {
    val protocol = fixture.copy(secondReadingRequiredWhenFirstElevated = false)

    val outcome = (ScreeningClassifier(protocol).classify(input(first = BpReading(150, 95), second = null)) as ScreeningClassification.Result).outcome

    assertThat(outcome).isEqualTo(CLINICAL_CONFIRMATION_REQUIRED)
  }

  @Test
  fun `next action due dates come from the protocol`() {
    assertThat(classifier.nextActionDueIn(URGENT_CLINICAL_ASSESSMENT).toDays()).isEqualTo(0)
    assertThat(classifier.nextActionDueIn(REPEAT_REQUIRED).toDays()).isEqualTo(fixture.repeatScreeningDueInDays.toLong())
    assertThat(classifier.nextActionDueIn(CLINICAL_CONFIRMATION_REQUIRED).toDays()).isEqualTo(fixture.confirmationReferralDueInDays.toLong())
  }

  @Test
  fun `unknown outcome codes from a newer server fall back to clinician review`() {
    assertThat(ScreeningOutcome.fromCode("something_new")).isEqualTo(NEEDS_CLINICIAN_REVIEW)
  }
}
