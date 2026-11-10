package org.simple.clinic.chw.screening.entry

import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningInput
import org.simple.clinic.chw.screening.SpecialPopulation
import org.simple.clinic.widgets.UiEvent
import java.time.Instant
import java.util.UUID

/*
 * Analytics names deliberately contain no values (no readings, no symptoms, no identifiers).
 */
sealed interface NewScreeningEvent : UiEvent {

  data class ScreenLoaded(
      val patientName: String?,
      val clinicName: String,
      val protocolApproved: Boolean,
      val minimumMinutesBetweenReadings: Int,
      val isChwRole: Boolean
  ) : NewScreeningEvent

  data class ConsentChanged(val given: Boolean) : NewScreeningEvent {
    override val analyticsName = "CHW Screening:Consent Changed"
  }

  data class LocationSelected(val location: ScreeningLocationType) : NewScreeningEvent

  data class FirstSystolicChanged(val text: String) : NewScreeningEvent
  data class FirstDiastolicChanged(val text: String) : NewScreeningEvent
  data class SecondSystolicChanged(val text: String) : NewScreeningEvent
  data class SecondDiastolicChanged(val text: String) : NewScreeningEvent

  /** [at] is supplied by the UI from the clock so that Update stays pure. */
  data class FirstReadingSaveClicked(val at: Instant) : NewScreeningEvent {
    override val analyticsName = "CHW Screening:Reading 1 Saved"
  }

  data object FirstReadingEditClicked : NewScreeningEvent

  data class SecondReadingSaveClicked(val at: Instant) : NewScreeningEvent {
    override val analyticsName = "CHW Screening:Reading 2 Saved"
  }

  data object SecondReadingEditClicked : NewScreeningEvent

  data class DangerSignToggled(val sign: DangerSign) : NewScreeningEvent

  data class PopulationSelected(val population: SpecialPopulation) : NewScreeningEvent

  data object SaveClicked : NewScreeningEvent {
    override val analyticsName = "CHW Screening:Save Clicked"
  }

  data class ScreeningSaved(val saved: SavedScreening) : NewScreeningEvent

  data class ScreeningRejected(val error: NewScreeningError) : NewScreeningEvent

  data object DoneClicked : NewScreeningEvent
}

sealed interface NewScreeningEffect {
  data class LoadScreen(val patientUuid: UUID) : NewScreeningEffect

  data class SaveScreening(
      val patientUuid: UUID,
      val location: ScreeningLocationType,
      val consentGiven: Boolean,
      val input: ScreeningInput
  ) : NewScreeningEffect
}

sealed interface NewScreeningViewEffect : NewScreeningEffect {
  data object GoBack : NewScreeningViewEffect
}
