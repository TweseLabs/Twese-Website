package org.simple.clinic.chw.screening.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spotify.mobius.rx2.RxMobius
import org.simple.clinic.mobius.MobiusViewModel
import java.util.UUID

class NewScreeningViewModel(
    patientUuid: UUID,
    savedStateHandle: SavedStateHandle,
    effectHandlerFactory: NewScreeningEffectHandler.Factory
) : MobiusViewModel<NewScreeningModel, NewScreeningEvent, NewScreeningEffect, NewScreeningViewEffect>(
    modelKey = NewScreeningModel::class.java.name,
    savedStateHandle = savedStateHandle,
    defaultModel = NewScreeningModel.create(patientUuid),
    init = NewScreeningInit(),
    loopFactoryProvider = { viewEffectsConsumer ->
      RxMobius.loop(
          NewScreeningUpdate(),
          effectHandlerFactory.create(viewEffectsConsumer).build()
      )
    }
) {

  companion object {
    fun factory(patientUuid: UUID, effectHandlerFactory: NewScreeningEffectHandler.Factory) = viewModelFactory {
      initializer {
        NewScreeningViewModel(
            patientUuid = patientUuid,
            savedStateHandle = createSavedStateHandle(),
            effectHandlerFactory = effectHandlerFactory
        )
      }
    }
  }
}
