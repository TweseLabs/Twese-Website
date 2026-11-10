package org.simple.clinic.chw.screening.entry

import com.spotify.mobius.First
import com.spotify.mobius.Init
import com.spotify.mobius.Next
import com.spotify.mobius.Update
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningInput
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.ConsentChanged
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.DangerSignToggled
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.DoneClicked
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.FirstDiastolicChanged
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.FirstReadingEditClicked
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.FirstReadingSaveClicked
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.FirstSystolicChanged
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.LocationSelected
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.PopulationSelected
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.SaveClicked
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.ScreenLoaded
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.ScreeningRejected
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.ScreeningSaved
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.SecondDiastolicChanged
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.SecondReadingEditClicked
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.SecondReadingSaveClicked
import org.simple.clinic.chw.screening.entry.NewScreeningEvent.SecondSystolicChanged
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.first
import org.simple.clinic.mobius.next

class NewScreeningInit : Init<NewScreeningModel, NewScreeningEffect> {
  override fun init(model: NewScreeningModel): First<NewScreeningModel, NewScreeningEffect> {
    return if (model.hasLoaded) first(model) else first(model, NewScreeningEffect.LoadScreen(model.patientUuid))
  }
}

class NewScreeningUpdate : Update<NewScreeningModel, NewScreeningEvent, NewScreeningEffect> {

  override fun update(model: NewScreeningModel, event: NewScreeningEvent): Next<NewScreeningModel, NewScreeningEffect> {
    if (model.saved != null && event !is DoneClicked) return Next.noChange()

    return when (event) {
      is ScreenLoaded -> next(model.copy(
          patientName = event.patientName,
          clinicName = event.clinicName,
          protocolApproved = event.protocolApproved,
          minimumMinutesBetweenReadings = event.minimumMinutesBetweenReadings,
          isChwRole = event.isChwRole
      ))
      is ConsentChanged -> next(model.copy(consentGiven = event.given, errors = model.errors - NewScreeningError.CONSENT_REQUIRED))
      is LocationSelected -> next(model.copy(location = event.location))
      is FirstSystolicChanged -> next(model.copy(firstSystolic = event.text.digitsOnly(), errors = model.errors - readingErrors))
      is FirstDiastolicChanged -> next(model.copy(firstDiastolic = event.text.digitsOnly(), errors = model.errors - readingErrors))
      is SecondSystolicChanged -> next(model.copy(secondSystolic = event.text.digitsOnly(), errors = model.errors - readingErrors))
      is SecondDiastolicChanged -> next(model.copy(secondDiastolic = event.text.digitsOnly(), errors = model.errors - readingErrors))
      is FirstReadingSaveClicked -> if (model.firstReading == null) {
        next(model.copy(errors = model.errors + NewScreeningError.FIRST_READING_NOT_A_NUMBER))
      } else {
        next(model.copy(firstSavedAt = event.at, errors = model.errors - NewScreeningError.FIRST_READING_REQUIRED))
      }
      FirstReadingEditClicked -> next(model.copy(firstSavedAt = null, secondSavedAt = null))
      is SecondReadingSaveClicked -> if (model.secondReading == null) {
        next(model.copy(errors = model.errors + NewScreeningError.SECOND_READING_NOT_A_NUMBER))
      } else {
        next(model.copy(secondSavedAt = event.at))
      }
      SecondReadingEditClicked -> next(model.copy(secondSavedAt = null))
      is DangerSignToggled -> next(model.copy(dangerSigns = toggle(model.dangerSigns, event.sign), errors = model.errors - NewScreeningError.SYMPTOMS_REQUIRED))
      is PopulationSelected -> next(model.copy(population = event.population, errors = model.errors - NewScreeningError.POPULATION_REQUIRED))
      SaveClicked -> onSave(model)
      is ScreeningSaved -> next(model.copy(isSaving = false, saved = event.saved, errors = emptySet()))
      is ScreeningRejected -> next(model.copy(isSaving = false, errors = model.errors + event.error))
      DoneClicked -> dispatch(NewScreeningViewEffect.GoBack)
    }
  }

  private fun onSave(model: NewScreeningModel): Next<NewScreeningModel, NewScreeningEffect> {
    if (model.isSaving) return Next.noChange()

    val errors = buildSet {
      if (!model.consentGiven) add(NewScreeningError.CONSENT_REQUIRED)
      if (model.firstSavedAt == null || model.firstReading == null) add(NewScreeningError.FIRST_READING_REQUIRED)
      if (model.dangerSigns.isEmpty()) add(NewScreeningError.SYMPTOMS_REQUIRED)
      if (model.population == null) add(NewScreeningError.POPULATION_REQUIRED)
    }
    if (errors.isNotEmpty()) return next(model.copy(errors = errors))

    // An unsaved (not confirmed) second reading is ignored rather than guessed.
    val second = if (model.secondSavedAt != null) model.secondReading else null
    val effect = NewScreeningEffect.SaveScreening(
        patientUuid = model.patientUuid,
        location = model.location,
        consentGiven = model.consentGiven,
        input = ScreeningInput(
            first = model.firstReading,
            second = second,
            minutesBetweenReadings = if (second != null) model.minutesBetweenReadings else null,
            dangerSigns = model.dangerSigns,
            specialPopulation = model.population!!
        )
    )
    return next(model.copy(isSaving = true, errors = emptySet()), effect)
  }

  /** "None" is mutually exclusive with every danger sign. */
  private fun toggle(current: Set<DangerSign>, sign: DangerSign): Set<DangerSign> = when {
    sign in current -> current - sign
    sign == DangerSign.NONE -> setOf(DangerSign.NONE)
    else -> current - DangerSign.NONE + sign
  }

  private fun String.digitsOnly() = filter(Char::isDigit).take(3)

  private val readingErrors = setOf(
      NewScreeningError.FIRST_READING_NOT_A_NUMBER,
      NewScreeningError.SECOND_READING_NOT_A_NUMBER,
      NewScreeningError.IMPLAUSIBLE_READING
  )
}
