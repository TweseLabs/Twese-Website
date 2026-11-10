package org.simple.clinic.chw.screening

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Versioned, data-driven community screening rules.
 *
 * NOTHING in this file is a clinically approved Burundi protocol. The values in
 * [ScreeningProtocol.DRAFT_UNAPPROVED] exist only so the software can be built and tested; they
 * are listed for review in docs/burundi/clinical-rules-to-approve.md and MUST be replaced by a
 * protocol signed off by qualified Burundi clinicians before any pilot.
 *
 * While [approved] is false the UI shows a persistent "draft protocol" banner.
 *
 * The protocol only produces a *triage / workflow* outcome. It never diagnoses hypertension,
 * never proposes a medicine and never proposes a dose.
 */
@JsonClass(generateAdapter = true)
data class ScreeningProtocol(
    @Json(name = "version")
    val version: String,

    @Json(name = "approved")
    val approved: Boolean,

    @Json(name = "approved_by")
    val approvedBy: String?,

    /** Readings outside these bounds are rejected as implausible (re-measure). */
    @Json(name = "plausible_systolic_min")
    val plausibleSystolicMin: Int,
    @Json(name = "plausible_systolic_max")
    val plausibleSystolicMax: Int,
    @Json(name = "plausible_diastolic_min")
    val plausibleDiastolicMin: Int,
    @Json(name = "plausible_diastolic_max")
    val plausibleDiastolicMax: Int,

    /** A reading at or above either value counts as "elevated" for screening purposes. */
    @Json(name = "elevated_systolic")
    val elevatedSystolic: Int,
    @Json(name = "elevated_diastolic")
    val elevatedDiastolic: Int,

    /** A reading at or above either value routes to urgent clinical assessment. */
    @Json(name = "urgent_systolic")
    val urgentSystolic: Int,
    @Json(name = "urgent_diastolic")
    val urgentDiastolic: Int,

    /** Whether an elevated first reading must be repeated before any decision. */
    @Json(name = "second_reading_required_when_first_elevated")
    val secondReadingRequiredWhenFirstElevated: Boolean,

    /** Minimum minutes between first and second reading. */
    @Json(name = "minimum_minutes_between_readings")
    val minimumMinutesBetweenReadings: Int,

    /**
     * If the two readings differ by more than this (systolic, mmHg) the result is "repeat
     * required" because the measurement may be unreliable.
     */
    @Json(name = "max_systolic_difference_between_readings")
    val maxSystolicDifferenceBetweenReadings: Int,

    /** Codes of symptoms that make the outcome urgent regardless of BP. */
    @Json(name = "danger_sign_codes")
    val dangerSignCodes: List<String>,

    /** Days until the next action for each outcome. */
    @Json(name = "repeat_screening_due_in_days")
    val repeatScreeningDueInDays: Int,
    @Json(name = "confirmation_referral_due_in_days")
    val confirmationReferralDueInDays: Int,
    @Json(name = "clinician_review_due_in_days")
    val clinicianReviewDueInDays: Int,
    @Json(name = "normal_rescreen_due_in_days")
    val normalRescreenDueInDays: Int
) {

  companion object {

    /**
     * Placeholder values, loosely shaped after WHO HEARTS conventions, for engineering use only.
     * NOT APPROVED FOR CLINICAL USE.
     */
    val DRAFT_UNAPPROVED = ScreeningProtocol(
        version = "draft-0.1-unapproved",
        approved = false,
        approvedBy = null,
        plausibleSystolicMin = 70,
        plausibleSystolicMax = 300,
        plausibleDiastolicMin = 40,
        plausibleDiastolicMax = 200,
        elevatedSystolic = 140,
        elevatedDiastolic = 90,
        urgentSystolic = 180,
        urgentDiastolic = 110,
        secondReadingRequiredWhenFirstElevated = true,
        minimumMinutesBetweenReadings = 1,
        maxSystolicDifferenceBetweenReadings = 25,
        dangerSignCodes = DangerSign.entries.filter { it != DangerSign.NONE }.map { it.code },
        repeatScreeningDueInDays = 7,
        confirmationReferralDueInDays = 14,
        clinicianReviewDueInDays = 7,
        normalRescreenDueInDays = 365
    )
  }
}

/**
 * Structured symptom checklist. Codes are stable API values; labels are localised resources.
 * The clinical list itself requires approval (docs/burundi/clinical-rules-to-approve.md).
 */
enum class DangerSign(val code: String) {
  SEVERE_HEADACHE("severe_headache"),
  CHEST_PAIN("chest_pain"),
  SHORTNESS_OF_BREATH("shortness_of_breath"),
  WEAKNESS_NUMBNESS_OR_SPEECH_DIFFICULTY("weakness_numbness_speech"),
  VISION_CHANGES("vision_changes"),
  CONFUSION_OR_REDUCED_CONSCIOUSNESS("confusion"),
  NONE("none");

  companion object {
    fun fromCode(code: String): DangerSign? = entries.firstOrNull { it.code == code }
  }
}

/** Special populations that always route to clinician review under the draft protocol. */
enum class SpecialPopulation(val code: String) {
  NONE("none"),
  PREGNANT_OR_RECENTLY_DELIVERED("pregnant_or_postpartum"),
  UNDER_18("under_18"),
  UNKNOWN("unknown");

  companion object {
    fun fromCode(code: String?): SpecialPopulation = entries.firstOrNull { it.code == code } ?: UNKNOWN
  }
}
