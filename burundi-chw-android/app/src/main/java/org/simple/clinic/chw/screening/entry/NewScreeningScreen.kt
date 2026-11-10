package org.simple.clinic.chw.screening.entry

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import kotlinx.parcelize.Parcelize
import org.simple.clinic.R
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningOutcome
import org.simple.clinic.chw.screening.SpecialPopulation
import org.simple.clinic.chw.ui.BannerTone
import org.simple.clinic.chw.ui.CheckRow
import org.simple.clinic.chw.ui.ChwBanner
import org.simple.clinic.chw.ui.ChwSectionCard
import org.simple.clinic.chw.ui.RadioGroup
import org.simple.clinic.chw.ui.dangerSignLabel
import org.simple.clinic.chw.ui.formatDate
import org.simple.clinic.chw.ui.outcomeLabel
import org.simple.clinic.chw.ui.populationLabel
import org.simple.clinic.common.ui.components.FilledButton
import org.simple.clinic.common.ui.components.TopAppBar
import org.simple.clinic.common.ui.theme.SimpleTheme
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.DisposableViewEffect
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.unsafeLazy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class NewScreeningScreen : Fragment() {

  @Inject
  lateinit var effectHandlerFactory: NewScreeningEffectHandler.Factory

  @Inject
  lateinit var router: Router

  @Inject
  lateinit var utcClock: UtcClock

  private val screenKey by unsafeLazy { ScreenKey.key<Key>(this) }

  private val viewModel by viewModels<NewScreeningViewModel>(
      factoryProducer = { NewScreeningViewModel.factory(screenKey.patientUuid, effectHandlerFactory) }
  )

  override fun onAttach(context: Context) {
    super.onAttach(context)
    context.injector<Injector>().inject(this)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        SimpleTheme {
          viewModel.viewEffects.DisposableViewEffect { effect ->
            when (effect) {
              NewScreeningViewEffect.GoBack -> router.pop()
            }
          }
          val model = viewModel.models.observeAsState().value
          if (model != null) {
            NewScreeningContent(
                model = model,
                now = { Instant.now(utcClock) },
                onEvent = viewModel::dispatch,
                onBack = { router.pop() }
            )
          }
        }
      }
    }
  }

  interface Injector {
    fun inject(target: NewScreeningScreen)
  }

  @Parcelize
  data class Key(
      val patientUuid: UUID,
      override val analyticsName: String = "CHW New Screening"
  ) : ScreenKey() {
    override fun instantiateFragment() = NewScreeningScreen()
  }
}

@Composable
private fun NewScreeningContent(
    model: NewScreeningModel,
    now: () -> Instant,
    onEvent: (NewScreeningEvent) -> Unit,
    onBack: () -> Unit
) {
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        },
        title = { Text(model.patientName ?: stringResource(R.string.chw_screening_title)) }
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      if (model.protocolApproved == false) ChwBanner(stringResource(R.string.chw_draft_protocol_banner), BannerTone.WARNING)

      val saved = model.saved
      when {
        !model.hasLoaded -> Unit
        model.isChwRole == false -> ChwBanner(stringResource(R.string.chw_role_not_chw), BannerTone.WARNING, Modifier.padding(8.dp))
        saved != null -> ResultView(saved, model.clinicName.orEmpty(), onDone = { onEvent(NewScreeningEvent.DoneClicked) })
        else -> FormView(model, now, onEvent)
      }
    }
  }
}

@Composable
private fun FormView(model: NewScreeningModel, now: () -> Instant, onEvent: (NewScreeningEvent) -> Unit) {
  val errors = model.errors

  ChwSectionCard(stringResource(R.string.chw_consent_title)) {
    Text(stringResource(R.string.chw_consent_body, org.simple.clinic.chw.ChwActions.CONSENT_VERSION), style = MaterialTheme.typography.body2)
    CheckRow(stringResource(R.string.chw_consent_checkbox), model.consentGiven) { onEvent(NewScreeningEvent.ConsentChanged(!model.consentGiven)) }
    if (NewScreeningError.CONSENT_REQUIRED in errors) ErrorText(stringResource(R.string.chw_error_consent_required))
  }

  ChwSectionCard(stringResource(R.string.chw_location_title)) {
    RadioGroup(
        options = listOf(
            ScreeningLocationType.HOUSEHOLD to stringResource(R.string.chw_location_household),
            ScreeningLocationType.COMMUNITY_SCREENING_POINT to stringResource(R.string.chw_location_screening_point),
            ScreeningLocationType.OTHER to stringResource(R.string.chw_location_other)
        ),
        selected = model.location,
        onSelect = { onEvent(NewScreeningEvent.LocationSelected(it)) }
    )
  }

  ChwBanner(stringResource(R.string.chw_measurement_instructions), BannerTone.INFO, Modifier.padding(horizontal = 8.dp))

  ReadingCard(
      title = stringResource(R.string.chw_first_reading),
      hint = null,
      systolic = model.firstSystolic,
      diastolic = model.firstDiastolic,
      savedAt = model.firstSavedAt,
      enabled = true,
      notANumber = NewScreeningError.FIRST_READING_NOT_A_NUMBER in errors || NewScreeningError.FIRST_READING_REQUIRED in errors,
      testTagPrefix = "first",
      onSystolic = { onEvent(NewScreeningEvent.FirstSystolicChanged(it)) },
      onDiastolic = { onEvent(NewScreeningEvent.FirstDiastolicChanged(it)) },
      onSave = { onEvent(NewScreeningEvent.FirstReadingSaveClicked(now())) },
      onEdit = { onEvent(NewScreeningEvent.FirstReadingEditClicked) }
  )

  ReadingCard(
      title = stringResource(R.string.chw_second_reading),
      hint = stringResource(R.string.chw_second_reading_hint, model.minimumMinutesBetweenReadings),
      systolic = model.secondSystolic,
      diastolic = model.secondDiastolic,
      savedAt = model.secondSavedAt,
      enabled = model.firstSavedAt != null,
      notANumber = NewScreeningError.SECOND_READING_NOT_A_NUMBER in errors,
      testTagPrefix = "second",
      onSystolic = { onEvent(NewScreeningEvent.SecondSystolicChanged(it)) },
      onDiastolic = { onEvent(NewScreeningEvent.SecondDiastolicChanged(it)) },
      onSave = { onEvent(NewScreeningEvent.SecondReadingSaveClicked(now())) },
      onEdit = { onEvent(NewScreeningEvent.SecondReadingEditClicked) }
  )
  if (NewScreeningError.IMPLAUSIBLE_READING in errors) ErrorText(stringResource(R.string.chw_error_implausible_reading), Modifier.padding(horizontal = 16.dp))

  ChwSectionCard(stringResource(R.string.chw_symptoms_title)) {
    DangerSign.entries.forEach { sign ->
      CheckRow(dangerSignLabel(sign), sign in model.dangerSigns) { onEvent(NewScreeningEvent.DangerSignToggled(sign)) }
    }
    if (NewScreeningError.SYMPTOMS_REQUIRED in errors) ErrorText(stringResource(R.string.chw_error_symptoms_required))
  }

  ChwSectionCard(stringResource(R.string.chw_special_population_title)) {
    RadioGroup(
        options = SpecialPopulation.entries.map { it to populationLabel(it) },
        selected = model.population,
        onSelect = { onEvent(NewScreeningEvent.PopulationSelected(it)) }
    )
    if (NewScreeningError.POPULATION_REQUIRED in errors) ErrorText(stringResource(R.string.chw_error_population_required))
  }

  listOf(
      NewScreeningError.NOT_PERMITTED to R.string.chw_error_not_permitted,
      NewScreeningError.ASSIGNED_TO_OTHER_CHW to R.string.chw_error_assigned_to_other,
      NewScreeningError.SAVE_FAILED to R.string.chw_error_generic
  ).filter { it.first in errors }.forEach { ChwBanner(stringResource(it.second), BannerTone.DANGER, Modifier.padding(8.dp)) }

  FilledButton(
      onClick = { onEvent(NewScreeningEvent.SaveClicked) },
      enabled = !model.isSaving,
      modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("chw_save_screening")
  ) {
    Text(stringResource(if (model.isSaving) R.string.chw_saving else R.string.chw_save_screening))
  }
}

@Composable
private fun ReadingCard(
    title: String,
    hint: String?,
    systolic: String,
    diastolic: String,
    savedAt: Instant?,
    enabled: Boolean,
    notANumber: Boolean,
    testTagPrefix: String,
    onSystolic: (String) -> Unit,
    onDiastolic: (String) -> Unit,
    onSave: () -> Unit,
    onEdit: () -> Unit
) {
  ChwSectionCard(title) {
    if (hint != null) Text(hint, style = MaterialTheme.typography.caption)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
          value = systolic,
          onValueChange = onSystolic,
          enabled = enabled && savedAt == null,
          label = { Text(stringResource(R.string.chw_systolic)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          isError = notANumber,
          modifier = Modifier.weight(1f).testTag("${testTagPrefix}_systolic")
      )
      OutlinedTextField(
          value = diastolic,
          onValueChange = onDiastolic,
          enabled = enabled && savedAt == null,
          label = { Text(stringResource(R.string.chw_diastolic)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          isError = notANumber,
          modifier = Modifier.weight(1f).testTag("${testTagPrefix}_diastolic")
      )
    }
    if (notANumber) ErrorText(stringResource(R.string.chw_error_enter_reading))
    Spacer(Modifier.height(8.dp))
    if (savedAt == null) {
      FilledButton(onClick = onSave, enabled = enabled, modifier = Modifier.testTag("${testTagPrefix}_save")) {
        Text(stringResource(R.string.chw_save_reading))
      }
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.chw_reading_saved_at, DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(savedAt)),
            style = MaterialTheme.typography.body2,
            modifier = Modifier.weight(1f)
        )
        org.simple.clinic.common.ui.components.TextButton(onClick = onEdit) { Text(stringResource(R.string.chw_edit_reading)) }
      }
    }
  }
}

@Composable
private fun ResultView(saved: SavedScreening, clinicName: String, onDone: () -> Unit) {
  val due = formatDate(saved.nextActionDue)
  val urgent = saved.outcome == ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT
  ChwSectionCard(stringResource(R.string.chw_result_title), Modifier.testTag("chw_screening_result")) {
    Text(outcomeLabel(saved.outcome), style = MaterialTheme.typography.h6)
    Spacer(Modifier.height(8.dp))
    val guidance = when (saved.outcome) {
      ScreeningOutcome.NORMAL_SCREENING -> stringResource(R.string.chw_result_normal_guidance, due)
      ScreeningOutcome.REPEAT_REQUIRED -> stringResource(R.string.chw_result_repeat_guidance, due)
      ScreeningOutcome.CLINICAL_CONFIRMATION_REQUIRED -> stringResource(R.string.chw_result_confirmation_guidance, clinicName, due)
      ScreeningOutcome.URGENT_CLINICAL_ASSESSMENT -> stringResource(R.string.chw_result_urgent_guidance, clinicName)
      ScreeningOutcome.NEEDS_CLINICIAN_REVIEW -> stringResource(R.string.chw_result_review_guidance, clinicName, due)
    }
    if (urgent) ChwBanner(guidance, BannerTone.DANGER) else Text(guidance)
  }
  ChwBanner(stringResource(R.string.chw_result_saved_on_phone), BannerTone.WARNING, Modifier.padding(8.dp))
  if (!saved.protocolApproved) ChwBanner(stringResource(R.string.chw_draft_protocol_banner), BannerTone.WARNING, Modifier.padding(horizontal = 8.dp))
  FilledButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("chw_screening_done")) {
    Text(stringResource(R.string.chw_done))
  }
}

@Composable
private fun ErrorText(text: String, modifier: Modifier = Modifier) {
  Text(text, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption, modifier = modifier)
}
