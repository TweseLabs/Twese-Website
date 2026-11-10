package org.simple.clinic.chw.screening.entry

import com.google.common.truth.Truth.assertThat
import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasModel
import com.spotify.mobius.test.NextMatchers.hasNoEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.screening.BpReading
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningInput
import org.simple.clinic.chw.screening.ScreeningOutcome
import org.simple.clinic.chw.screening.SpecialPopulation
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class NewScreeningUpdateTest {

  private val spec = UpdateSpec(NewScreeningUpdate())
  private val patient = UUID.fromString("5b3e3d7e-0000-4000-8000-000000000001")
  private val t0 = Instant.parse("2026-03-10T08:00:00Z")

  private val loaded = NewScreeningModel.create(patient).copy(
      patientName = "Synthetic Person",
      clinicName = "Synthetic Clinic",
      protocolApproved = false,
      isChwRole = true
  )

  private val readyToSave = loaded.copy(
      consentGiven = true,
      firstSystolic = "150", firstDiastolic = "95", firstSavedAt = t0,
      secondSystolic = "146", secondDiastolic = "92", secondSavedAt = t0.plusSeconds(180),
      dangerSigns = setOf(DangerSign.NONE),
      population = SpecialPopulation.NONE
  )

  @Test
  fun `saving a complete two-reading screening dispatches the save effect with minutes between readings`() {
    spec.given(readyToSave)
        .whenEvent(NewScreeningEvent.SaveClicked)
        .then(assertThatNext(
            hasModel(readyToSave.copy(isSaving = true)),
            hasEffects(NewScreeningEffect.SaveScreening(
                patientUuid = patient,
                location = ScreeningLocationType.HOUSEHOLD,
                consentGiven = true,
                input = ScreeningInput(BpReading(150, 95), BpReading(146, 92), 3, setOf(DangerSign.NONE), SpecialPopulation.NONE)
            ))
        ))
  }

  @Test
  fun `save is blocked with all missing-field errors and no effect`() {
    spec.given(loaded)
        .whenEvent(NewScreeningEvent.SaveClicked)
        .then(assertThatNext(
            hasModel(loaded.copy(errors = setOf(
                NewScreeningError.CONSENT_REQUIRED,
                NewScreeningError.FIRST_READING_REQUIRED,
                NewScreeningError.SYMPTOMS_REQUIRED,
                NewScreeningError.POPULATION_REQUIRED
            ))),
            hasNoEffects()
        ))
  }

  @Test
  fun `an unconfirmed second reading is ignored rather than guessed`() {
    val model = readyToSave.copy(secondSavedAt = null)

    spec.given(model)
        .whenEvent(NewScreeningEvent.SaveClicked)
        .then(assertThatNext(
            hasEffects(NewScreeningEffect.SaveScreening(
                patientUuid = patient,
                location = ScreeningLocationType.HOUSEHOLD,
                consentGiven = true,
                input = ScreeningInput(BpReading(150, 95), null, null, setOf(DangerSign.NONE), SpecialPopulation.NONE)
            ))
        ))
  }

  @Test
  fun `selecting a danger sign clears 'none', and selecting 'none' clears danger signs`() {
    val withNone = loaded.copy(dangerSigns = setOf(DangerSign.NONE))
    spec.given(withNone)
        .whenEvent(NewScreeningEvent.DangerSignToggled(DangerSign.CHEST_PAIN))
        .then(assertThatNext(hasModel(withNone.copy(dangerSigns = setOf(DangerSign.CHEST_PAIN)))))

    val withSign = loaded.copy(dangerSigns = setOf(DangerSign.CHEST_PAIN, DangerSign.VISION_CHANGES))
    spec.given(withSign)
        .whenEvent(NewScreeningEvent.DangerSignToggled(DangerSign.NONE))
        .then(assertThatNext(hasModel(withSign.copy(dangerSigns = setOf(DangerSign.NONE)))))
  }

  @Test
  fun `non digit input is stripped and limited to three digits`() {
    spec.given(loaded)
        .whenEvent(NewScreeningEvent.FirstSystolicChanged("1a2b34"))
        .then(assertThatNext(hasModel(loaded.copy(firstSystolic = "123"))))
  }

  @Test
  fun `saving reading 1 without numbers shows an error and does not stamp a time`() {
    spec.given(loaded)
        .whenEvent(NewScreeningEvent.FirstReadingSaveClicked(t0))
        .then(assertThatNext(hasModel(loaded.copy(errors = setOf(NewScreeningError.FIRST_READING_NOT_A_NUMBER)))))
  }

  @Test
  fun `editing reading 1 also invalidates reading 2 so the interval cannot be wrong`() {
    spec.given(readyToSave)
        .whenEvent(NewScreeningEvent.FirstReadingEditClicked)
        .then(assertThatNext(hasModel(readyToSave.copy(firstSavedAt = null, secondSavedAt = null))))
  }

  @Test
  fun `double tapping save while saving does nothing`() {
    spec.given(readyToSave.copy(isSaving = true))
        .whenEvent(NewScreeningEvent.SaveClicked)
        .then(assertThatNext(hasNoModel(), hasNoEffects()))
  }

  @Test
  fun `once saved, the form cannot be changed and Done goes back`() {
    val saved = readyToSave.copy(saved = SavedScreening(ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED, LocalDate.parse("2026-03-24"), false))

    spec.given(saved)
        .whenEvent(NewScreeningEvent.FirstSystolicChanged("120"))
        .then(assertThatNext(hasNoModel(), hasNoEffects()))

    spec.given(saved)
        .whenEvent(NewScreeningEvent.DoneClicked)
        .then(assertThatNext(hasNoModel(), hasEffects(NewScreeningViewEffect.GoBack)))
  }

  @Test
  fun `rejection from the planner is shown and saving stops`() {
    spec.given(readyToSave.copy(isSaving = true))
        .whenEvent(NewScreeningEvent.ScreeningRejected(NewScreeningError.ASSIGNED_TO_OTHER_CHW))
        .then(assertThatNext(hasModel(readyToSave.copy(errors = setOf(NewScreeningError.ASSIGNED_TO_OTHER_CHW)))))
  }

  @Test
  fun `init loads the screen only once`() {
    val first = NewScreeningInit().init(NewScreeningModel.create(patient))
    assertThat(first.effects()).containsExactly(NewScreeningEffect.LoadScreen(patient))

    assertThat(NewScreeningInit().init(loaded).effects()).isEmpty()
  }
}
