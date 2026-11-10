package org.simple.clinic.chw.screening

import java.time.Duration

/**
 * Workflow outcome of a community screening. This is triage, NOT a diagnosis.
 * API codes are stable; UI labels come from string resources.
 */
enum class ScreeningOutcome(val code: String) {
  NORMAL_SCREENING("normal_screening"),
  REPEAT_REQUIRED("repeat_required"),
  CLINICAL_CONFIRMATION_REQUIRED("clinical_confirmation_required"),
  URGENT_CLINICAL_ASSESSMENT("urgent_clinical_assessment"),
  NEEDS_CLINICIAN_REVIEW("needs_clinician_review");

  companion object {
    fun fromCode(code: String?): ScreeningOutcome =
        entries.firstOrNull { it.code == code } ?: NEEDS_CLINICIAN_REVIEW
  }
}

data class BpReading(val systolic: Int, val diastolic: Int)

data class ScreeningInput(
    val first: BpReading?,
    val second: BpReading?,
    val minutesBetweenReadings: Long?,
    val dangerSigns: Set<DangerSign>,
    val specialPopulation: SpecialPopulation
)

/** Why the classifier reached its decision (shown to CHW and stored for audit). */
enum class ScreeningReason(val code: String) {
  DANGER_SIGN_REPORTED("danger_sign_reported"),
  READING_AT_OR_ABOVE_URGENT_THRESHOLD("reading_at_or_above_urgent_threshold"),
  SPECIAL_POPULATION("special_population"),
  SECOND_READING_MISSING("second_reading_missing"),
  READINGS_TOO_CLOSE_IN_TIME("readings_too_close_in_time"),
  READINGS_INCONSISTENT("readings_inconsistent"),
  AVERAGE_AT_OR_ABOVE_ELEVATED_THRESHOLD("average_at_or_above_elevated_threshold"),
  AVERAGE_BELOW_ELEVATED_THRESHOLD("average_below_elevated_threshold"),
  NO_READING("no_reading"),
  SYMPTOM_CHECKLIST_NOT_COMPLETED("symptom_checklist_not_completed"),
  SYMPTOM_NOT_COVERED_BY_PROTOCOL("symptom_not_covered_by_protocol")
}

sealed interface ScreeningClassification {

  data class Result(
      val outcome: ScreeningOutcome,
      val reasons: List<ScreeningReason>,
      val protocolVersion: String,
      val protocolApproved: Boolean
  ) : ScreeningClassification

  /** Input cannot be classified because a reading is not physiologically plausible. */
  data class InvalidReading(val problems: List<ReadingProblem>) : ScreeningClassification
}

enum class ReadingProblem {
  FIRST_SYSTOLIC_OUT_OF_RANGE,
  FIRST_DIASTOLIC_OUT_OF_RANGE,
  FIRST_DIASTOLIC_NOT_BELOW_SYSTOLIC,
  SECOND_SYSTOLIC_OUT_OF_RANGE,
  SECOND_DIASTOLIC_OUT_OF_RANGE,
  SECOND_DIASTOLIC_NOT_BELOW_SYSTOLIC
}

/**
 * Pure function from (protocol, input) to a workflow outcome. Rule order, from most to least
 * safety-critical:
 *
 * 1. Implausible readings are rejected (re-measure); nothing is saved as a result.
 * 2. Any danger sign, or any reading at/above the urgent threshold → URGENT_CLINICAL_ASSESSMENT.
 * 3. Missing symptom checklist, a reported symptom the protocol does not cover, special
 *    population, or no reading → NEEDS_CLINICIAN_REVIEW.
 * 4. Elevated first reading with no valid second reading → REPEAT_REQUIRED.
 * 5. Inconsistent pair of readings → REPEAT_REQUIRED.
 * 6. Average elevated → CLINICAL_CONFIRMATION_REQUIRED.
 * 7. Otherwise → NORMAL_SCREENING.
 *
 * Anything the rules do not explicitly cover falls through to NEEDS_CLINICIAN_REVIEW.
 */
class ScreeningClassifier(private val protocol: ScreeningProtocol) {

  fun classify(input: ScreeningInput): ScreeningClassification {
    val problems = validate(input)
    if (problems.isNotEmpty()) return ScreeningClassification.InvalidReading(problems)

    val readings = listOfNotNull(input.first, input.second)
    val protocolDangerSigns = protocol.dangerSignCodes.toSet()
    val reportedDangerSigns = input.dangerSigns.filter { it != DangerSign.NONE && it.code in protocolDangerSigns }

    val urgentReasons = buildList {
      if (reportedDangerSigns.isNotEmpty()) add(ScreeningReason.DANGER_SIGN_REPORTED)
      if (readings.any(::isUrgent)) add(ScreeningReason.READING_AT_OR_ABOVE_URGENT_THRESHOLD)
    }
    if (urgentReasons.isNotEmpty()) return result(ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT, urgentReasons)

    if (input.dangerSigns.isEmpty()) {
      return result(ScreeningOutcome.NEEDS_CLINICIAN_REVIEW, listOf(ScreeningReason.SYMPTOM_CHECKLIST_NOT_COMPLETED))
    }
    val unlistedSymptoms = input.dangerSigns.filter { it != DangerSign.NONE && it.code !in protocolDangerSigns }
    if (unlistedSymptoms.isNotEmpty()) {
      return result(ScreeningOutcome.NEEDS_CLINICIAN_REVIEW, listOf(ScreeningReason.SYMPTOM_NOT_COVERED_BY_PROTOCOL))
    }
    if (input.specialPopulation != SpecialPopulation.NONE) {
      return result(ScreeningOutcome.NEEDS_CLINICIAN_REVIEW, listOf(ScreeningReason.SPECIAL_POPULATION))
    }
    val first = input.first ?: return result(ScreeningOutcome.NEEDS_CLINICIAN_REVIEW, listOf(ScreeningReason.NO_READING))
    val second = input.second

    if (second == null) {
      return if (isElevated(first) && protocol.secondReadingRequiredWhenFirstElevated) {
        result(ScreeningOutcome.REPEAT_REQUIRED, listOf(ScreeningReason.SECOND_READING_MISSING))
      } else if (isElevated(first)) {
        result(ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED, listOf(ScreeningReason.AVERAGE_AT_OR_ABOVE_ELEVATED_THRESHOLD))
      } else {
        result(ScreeningOutcome.NORMAL_SCREENING, listOf(ScreeningReason.AVERAGE_BELOW_ELEVATED_THRESHOLD))
      }
    }

    val minutes = input.minutesBetweenReadings
    if (minutes != null && minutes < protocol.minimumMinutesBetweenReadings) {
      return result(ScreeningOutcome.REPEAT_REQUIRED, listOf(ScreeningReason.READINGS_TOO_CLOSE_IN_TIME))
    }
    if (kotlin.math.abs(first.systolic - second.systolic) > protocol.maxSystolicDifferenceBetweenReadings) {
      return result(ScreeningOutcome.REPEAT_REQUIRED, listOf(ScreeningReason.READINGS_INCONSISTENT))
    }

    val average = BpReading(
        systolic = Math.round((first.systolic + second.systolic) / 2.0).toInt(),
        diastolic = Math.round((first.diastolic + second.diastolic) / 2.0).toInt()
    )
    return if (isElevated(average)) {
      result(ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED, listOf(ScreeningReason.AVERAGE_AT_OR_ABOVE_ELEVATED_THRESHOLD))
    } else {
      result(ScreeningOutcome.NORMAL_SCREENING, listOf(ScreeningReason.AVERAGE_BELOW_ELEVATED_THRESHOLD))
    }
  }

  /** Days until the follow-up/referral is due for an outcome, from the protocol. */
  fun nextActionDueIn(outcome: ScreeningOutcome): Duration = Duration.ofDays(
      when (outcome) {
        ScreeningOutcome.NORMAL_SCREENING -> protocol.normalRescreenDueInDays
        ScreeningOutcome.REPEAT_REQUIRED -> protocol.repeatScreeningDueInDays
        ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED -> protocol.confirmationReferralDueInDays
        ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT -> 0
        ScreeningOutcome.NEEDS_CLINICIAN_REVIEW -> protocol.clinicianReviewDueInDays
      }.toLong()
  )

  fun validate(input: ScreeningInput): List<ReadingProblem> = buildList {
    input.first?.let { r ->
      if (r.systolic !in protocol.plausibleSystolicMin..protocol.plausibleSystolicMax) add(ReadingProblem.FIRST_SYSTOLIC_OUT_OF_RANGE)
      if (r.diastolic !in protocol.plausibleDiastolicMin..protocol.plausibleDiastolicMax) add(ReadingProblem.FIRST_DIASTOLIC_OUT_OF_RANGE)
      if (r.diastolic >= r.systolic) add(ReadingProblem.FIRST_DIASTOLIC_NOT_BELOW_SYSTOLIC)
    }
    input.second?.let { r ->
      if (r.systolic !in protocol.plausibleSystolicMin..protocol.plausibleSystolicMax) add(ReadingProblem.SECOND_SYSTOLIC_OUT_OF_RANGE)
      if (r.diastolic !in protocol.plausibleDiastolicMin..protocol.plausibleDiastolicMax) add(ReadingProblem.SECOND_DIASTOLIC_OUT_OF_RANGE)
      if (r.diastolic >= r.systolic) add(ReadingProblem.SECOND_DIASTOLIC_NOT_BELOW_SYSTOLIC)
    }
  }

  private fun isElevated(r: BpReading) = r.systolic >= protocol.elevatedSystolic || r.diastolic >= protocol.elevatedDiastolic

  private fun isUrgent(r: BpReading) = r.systolic >= protocol.urgentSystolic || r.diastolic >= protocol.urgentDiastolic

  private fun result(outcome: ScreeningOutcome, reasons: List<ScreeningReason>) = ScreeningClassification.Result(
      outcome = outcome,
      reasons = reasons,
      protocolVersion = protocol.version,
      protocolApproved = protocol.approved
  )
}
