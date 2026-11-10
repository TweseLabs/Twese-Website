package org.simple.clinic.chw.followup.entry

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import kotlinx.parcelize.Parcelize
import org.simple.clinic.R
import org.simple.clinic.chw.ChwActions
import org.simple.clinic.chw.ContactMethod
import org.simple.clinic.chw.ContactOutcome
import org.simple.clinic.chw.ReportedMedicineAvailability
import org.simple.clinic.chw.followup.FollowUpPlanResult
import org.simple.clinic.chw.followup.FollowUpRejection
import org.simple.clinic.chw.followup.FollowUpRequest
import org.simple.clinic.chw.screening.BpReading
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.ui.BannerTone
import org.simple.clinic.chw.ui.CheckRow
import org.simple.clinic.chw.ui.ChwBanner
import org.simple.clinic.chw.ui.ChwSectionCard
import org.simple.clinic.chw.ui.RadioGroup
import org.simple.clinic.chw.ui.contactOutcomeLabel
import org.simple.clinic.chw.ui.dangerSignLabel
import org.simple.clinic.chw.ui.medicineLabel
import org.simple.clinic.common.ui.components.FilledButton
import org.simple.clinic.common.ui.components.TopAppBar
import org.simple.clinic.common.ui.theme.SimpleTheme
import org.simple.clinic.di.injector
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.unsafeLazy
import java.util.UUID
import javax.inject.Inject

/** Form state; kept as plain values so it survives configuration changes. */
@Parcelize
data class FollowUpForm(
    val method: ContactMethod = ContactMethod.HOME_VISIT,
    val outcome: ContactOutcome? = null,
    val systolic: String = "",
    val diastolic: String = "",
    val dangerSigns: Set<DangerSign> = emptySet(),
    val medicine: ReportedMedicineAvailability = ReportedMedicineAvailability.NOT_ASKED,
    val sideEffectConcern: Boolean = false,
    val nextInDays: Int? = 30,
    val note: String = ""
) : Parcelable

class ChwFollowUpScreen : Fragment() {

  @Inject lateinit var router: Router
  @Inject lateinit var chwActions: ChwActions
  @Inject lateinit var schedulersProvider: SchedulersProvider

  private val screenKey by unsafeLazy { ScreenKey.key<Key>(this) }
  private val disposables = CompositeDisposable()
  private var saving by mutableStateOf(false)
  private var error by mutableStateOf<Int?>(null)
  private var saved by mutableStateOf(false)

  override fun onAttach(context: Context) {
    super.onAttach(context)
    context.injector<Injector>().inject(this)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        SimpleTheme {
          var form by rememberSaveable { mutableStateOf(FollowUpForm()) }
          FollowUpContent(
              form = form,
              saving = saving,
              saved = saved,
              errorRes = error,
              onChange = { form = it; error = null },
              onSave = { save(form) },
              onBack = { router.pop() }
          )
        }
      }
    }
  }

  private fun save(form: FollowUpForm) {
    val outcome = form.outcome ?: run { error = R.string.chw_error_generic; return }
    val reading = if (outcome == ContactOutcome.REACHED && form.systolic.isNotBlank()) {
      val s = form.systolic.toIntOrNull()
      val d = form.diastolic.toIntOrNull()
      if (s == null || d == null) { error = R.string.chw_error_enter_reading; return }
      BpReading(s, d)
    } else null
    if (outcome == ContactOutcome.REACHED && form.dangerSigns.isEmpty()) {
      error = R.string.chw_error_symptoms_required
      return
    }
    saving = true
    disposables.add(
        Single.fromCallable {
          chwActions.recordFollowUp { ctx, now, today ->
            FollowUpRequest(
                patientId = screenKey.patientUuid,
                chwUserId = ctx.user.uuid,
                role = ctx.role,
                communityAreaId = ctx.assignment?.communityAreaId,
                supervisingFacilityId = ctx.supervisingFacilityId,
                taskId = screenKey.taskId,
                occurredAt = now,
                today = today,
                method = form.method,
                outcome = outcome,
                reading = reading,
                // Symptoms are only asked when the person was reached.
                dangerSigns = if (outcome == ContactOutcome.REACHED) form.dangerSigns else emptySet(),
                medicineAvailability = if (outcome == ContactOutcome.REACHED) form.medicine else ReportedMedicineAvailability.NOT_ASKED,
                sideEffectConcern = if (outcome == ContactOutcome.REACHED) form.sideEffectConcern else null,
                nextDueDate = form.nextInDays?.let { today.plusDays(it.toLong()) },
                note = form.note
            )
          }
        }
            .subscribeOn(schedulersProvider.io())
            .observeOn(schedulersProvider.ui())
            .subscribe({ result ->
              saving = false
              when (result) {
                is FollowUpPlanResult.Planned -> saved = true
                is FollowUpPlanResult.Rejected -> error = result.reason.messageRes()
              }
            }, { throwable ->
              CrashReporter.report(throwable)
              saving = false
              error = R.string.chw_error_generic
            })
    )
  }

  override fun onDestroyView() {
    disposables.clear()
    super.onDestroyView()
  }

  private fun FollowUpRejection.messageRes(): Int = when (this) {
    FollowUpRejection.NOT_PERMITTED_FOR_ROLE -> R.string.chw_error_not_permitted
    FollowUpRejection.PATIENT_NOT_ASSIGNED_TO_THIS_CHW -> R.string.chw_error_not_assigned
    FollowUpRejection.INVALID_READING -> R.string.chw_error_implausible_reading
    FollowUpRejection.NEXT_DUE_DATE_REQUIRED -> R.string.chw_error_next_date_required
    FollowUpRejection.NEXT_DUE_DATE_IN_PAST -> R.string.chw_error_next_date_past
    FollowUpRejection.NOTE_TOO_LONG -> R.string.chw_error_note_too_long
  }

  interface Injector {
    fun inject(target: ChwFollowUpScreen)
  }

  @Parcelize
  data class Key(
      val patientUuid: UUID,
      val taskId: UUID?,
      override val analyticsName: String = "CHW Follow Up"
  ) : ScreenKey() {
    override fun instantiateFragment() = ChwFollowUpScreen()
  }
}

@Composable
private fun FollowUpContent(
    form: FollowUpForm,
    saving: Boolean,
    saved: Boolean,
    errorRes: Int?,
    onChange: (FollowUpForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
        title = { Text(stringResource(R.string.chw_follow_up_title)) }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (saved) {
        ChwBanner(stringResource(R.string.chw_contact_saved) + " " + stringResource(R.string.chw_server_sync_unavailable), BannerTone.WARNING, Modifier.padding(8.dp))
        FilledButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("chw_follow_up_done")) { Text(stringResource(R.string.chw_done)) }
        return@Column
      }

      ChwSectionCard(stringResource(R.string.chw_contact_method_title)) {
        RadioGroup(
            listOf(
                ContactMethod.HOME_VISIT to stringResource(R.string.chw_contact_method_home),
                ContactMethod.PHONE_CALL to stringResource(R.string.chw_contact_method_phone),
                ContactMethod.COMMUNITY_POINT to stringResource(R.string.chw_contact_method_point)
            ),
            form.method
        ) { onChange(form.copy(method = it)) }
      }

      ChwSectionCard(stringResource(R.string.chw_contact_outcome_title)) {
        RadioGroup(
            ContactOutcome.entries.filter { it != ContactOutcome.UNKNOWN }.map { it to contactOutcomeLabel(it) },
            form.outcome
        ) {
          val needsNext = it.isSuccessful || it.isFailedAttempt
          onChange(form.copy(outcome = it, nextInDays = if (needsNext) form.nextInDays ?: if (it.isFailedAttempt) 3 else 30 else null))
        }
      }

      if (form.outcome == ContactOutcome.REACHED) {
        ChwSectionCard(stringResource(R.string.chw_reading_optional)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(form.systolic, { v -> onChange(form.copy(systolic = v.filter(Char::isDigit).take(3))) },
                label = { Text(stringResource(R.string.chw_systolic)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(form.diastolic, { v -> onChange(form.copy(diastolic = v.filter(Char::isDigit).take(3))) },
                label = { Text(stringResource(R.string.chw_diastolic)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
          }
        }
        ChwSectionCard(stringResource(R.string.chw_symptoms_title)) {
          DangerSign.entries.forEach { sign ->
            CheckRow(dangerSignLabel(sign), sign in form.dangerSigns) {
              val next = when {
                sign in form.dangerSigns -> form.dangerSigns - sign
                sign == DangerSign.NONE -> setOf(DangerSign.NONE)
                else -> form.dangerSigns - DangerSign.NONE + sign
              }
              onChange(form.copy(dangerSigns = next))
            }
          }
        }
        ChwSectionCard(stringResource(R.string.chw_medicine_title)) {
          RadioGroup(
              ReportedMedicineAvailability.entries.filter { it != ReportedMedicineAvailability.UNKNOWN }.map { it to medicineLabel(it) },
              form.medicine
          ) { onChange(form.copy(medicine = it)) }
          CheckRow(stringResource(R.string.chw_side_effect_concern), form.sideEffectConcern) { onChange(form.copy(sideEffectConcern = !form.sideEffectConcern)) }
          Text(stringResource(R.string.chw_prescriptions_clinic_only), style = MaterialTheme.typography.caption)
        }
      }

      if (form.outcome != null && (form.outcome.isSuccessful || form.outcome.isFailedAttempt)) {
        ChwSectionCard(stringResource(R.string.chw_next_visit_in)) {
          RadioGroup(listOf(3, 7, 14, 30, 90).map { it to stringResource(R.string.chw_days, it) }, form.nextInDays) { onChange(form.copy(nextInDays = it)) }
        }
      }

      ChwSectionCard(null) {
        OutlinedTextField(
            value = form.note,
            onValueChange = { onChange(form.copy(note = it.take(280))) },
            label = { Text(stringResource(R.string.chw_note_optional)) },
            modifier = Modifier.fillMaxWidth()
        )
      }

      if (errorRes != null) ChwBanner(stringResource(errorRes), BannerTone.DANGER, Modifier.padding(8.dp))

      FilledButton(onClick = onSave, enabled = !saving && form.outcome != null, modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("chw_save_contact")) {
        Text(stringResource(if (saving) R.string.chw_saving else R.string.chw_save_contact))
      }
    }
  }
}
