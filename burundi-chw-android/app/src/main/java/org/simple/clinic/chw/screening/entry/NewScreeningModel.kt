package org.simple.clinic.chw.screening.entry

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.screening.BpReading
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningOutcome
import org.simple.clinic.chw.screening.SpecialPopulation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class NewScreeningError {
  CONSENT_REQUIRED,
  FIRST_READING_REQUIRED,
  FIRST_READING_NOT_A_NUMBER,
  SECOND_READING_NOT_A_NUMBER,
  SYMPTOMS_REQUIRED,
  POPULATION_REQUIRED,
  IMPLAUSIBLE_READING,
  NOT_PERMITTED,
  ASSIGNED_TO_OTHER_CHW,
  SAVE_FAILED
}

@Parcelize
data class SavedScreening(
    val outcome: ScreeningOutcome,
    val nextActionDue: LocalDate,
    val protocolApproved: Boolean
) : Parcelable

@Parcelize
data class NewScreeningModel(
    val patientUuid: UUID,
    val patientName: String?,
    val clinicName: String?,
    val protocolApproved: Boolean?,
    val minimumMinutesBetweenReadings: Int,
    val isChwRole: Boolean?,
    val consentGiven: Boolean,
    val location: ScreeningLocationType,
    val firstSystolic: String,
    val firstDiastolic: String,
    val firstSavedAt: Instant?,
    val secondSystolic: String,
    val secondDiastolic: String,
    val secondSavedAt: Instant?,
    val dangerSigns: Set<DangerSign>,
    val population: SpecialPopulation?,
    val errors: Set<NewScreeningError>,
    val isSaving: Boolean,
    val saved: SavedScreening?
) : Parcelable {

  val firstReading: BpReading? get() = parse(firstSystolic, firstDiastolic)
  val secondReading: BpReading? get() = parse(secondSystolic, secondDiastolic)

  val minutesBetweenReadings: Long?
    get() = if (firstSavedAt != null && secondSavedAt != null) Duration.between(firstSavedAt, secondSavedAt).toMinutes() else null

  val hasLoaded: Boolean get() = isChwRole != null

  companion object {
    fun create(patientUuid: UUID) = NewScreeningModel(
        patientUuid = patientUuid,
        patientName = null,
        clinicName = null,
        protocolApproved = null,
        minimumMinutesBetweenReadings = 1,
        isChwRole = null,
        consentGiven = false,
        location = ScreeningLocationType.HOUSEHOLD,
        firstSystolic = "",
        firstDiastolic = "",
        firstSavedAt = null,
        secondSystolic = "",
        secondDiastolic = "",
        secondSavedAt = null,
        dangerSigns = emptySet(),
        population = null,
        errors = emptySet(),
        isSaving = false,
        saved = null
    )

    fun parse(systolic: String, diastolic: String): BpReading? {
      val s = systolic.trim().toIntOrNull() ?: return null
      val d = diastolic.trim().toIntOrNull() ?: return null
      return BpReading(s, d)
    }
  }
}
