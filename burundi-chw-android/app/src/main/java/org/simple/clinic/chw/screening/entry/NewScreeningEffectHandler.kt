package org.simple.clinic.chw.screening.entry

import com.spotify.mobius.functions.Consumer
import com.spotify.mobius.rx2.RxMobius
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.reactivex.ObservableTransformer
import org.simple.clinic.chw.ChwActions
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.screening.PlanResult
import org.simple.clinic.chw.screening.RejectionReason
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.util.scheduler.SchedulersProvider

class NewScreeningEffectHandler @AssistedInject constructor(
    private val chwActions: ChwActions,
    private val patientRepository: PatientRepository,
    private val schedulersProvider: SchedulersProvider,
    @Assisted private val viewEffectsConsumer: Consumer<NewScreeningViewEffect>
) {

  @AssistedFactory
  interface Factory {
    fun create(viewEffectsConsumer: Consumer<NewScreeningViewEffect>): NewScreeningEffectHandler
  }

  fun build(): ObservableTransformer<NewScreeningEffect, NewScreeningEvent> = RxMobius
      .subtypeEffectHandler<NewScreeningEffect, NewScreeningEvent>()
      .addTransformer(NewScreeningEffect.LoadScreen::class.java, loadScreen())
      .addTransformer(NewScreeningEffect.SaveScreening::class.java, saveScreening())
      .addConsumer(NewScreeningViewEffect::class.java, viewEffectsConsumer::accept)
      .build()

  private fun loadScreen() = ObservableTransformer<NewScreeningEffect.LoadScreen, NewScreeningEvent> { effects ->
    effects
        .observeOn(schedulersProvider.io())
        .map { effect ->
          val ctx = chwActions.context()
          NewScreeningEvent.ScreenLoaded(
              patientName = patientRepository.patientImmediate(effect.patientUuid)?.fullName,
              clinicName = chwActions.currentFacilityName(),
              protocolApproved = ctx.protocol.approved,
              minimumMinutesBetweenReadings = ctx.protocol.minimumMinutesBetweenReadings,
              isChwRole = ctx.role == ChwRole.CHW
          )
        }
  }

  private fun saveScreening() = ObservableTransformer<NewScreeningEffect.SaveScreening, NewScreeningEvent> { effects ->
    effects
        .observeOn(schedulersProvider.io())
        .map { effect ->
          try {
            when (val result = chwActions.recordScreening(effect.patientUuid, effect.location, effect.consentGiven, effect.input)) {
              is PlanResult.Planned -> NewScreeningEvent.ScreeningSaved(SavedScreening(
                  outcome = result.classification.outcome,
                  nextActionDue = result.plan.newTask.dueDate,
                  protocolApproved = result.classification.protocolApproved
              ))
              is PlanResult.Rejected -> NewScreeningEvent.ScreeningRejected(result.reason.toError())
            }
          } catch (e: Exception) {
            CrashReporter.report(e)
            NewScreeningEvent.ScreeningRejected(NewScreeningError.SAVE_FAILED)
          }
        }
  }

  private fun RejectionReason.toError() = when (this) {
    RejectionReason.NOT_PERMITTED_FOR_ROLE -> NewScreeningError.NOT_PERMITTED
    RejectionReason.CONSENT_NOT_RECORDED -> NewScreeningError.CONSENT_REQUIRED
    RejectionReason.PATIENT_ASSIGNED_TO_ANOTHER_CHW -> NewScreeningError.ASSIGNED_TO_OTHER_CHW
    RejectionReason.INVALID_READING -> NewScreeningError.IMPLAUSIBLE_READING
    RejectionReason.FIRST_READING_REQUIRED -> NewScreeningError.FIRST_READING_REQUIRED
  }
}
