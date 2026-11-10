package org.simple.clinic.chw.patient

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import kotlinx.parcelize.Parcelize
import org.simple.clinic.R
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.chw.AssignmentStatus
import org.simple.clinic.chw.ChwRepository
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.access.ChwSession
import org.simple.clinic.chw.area.PatientChwAssignment
import org.simple.clinic.chw.followup.ChwFollowUpTask
import org.simple.clinic.chw.followup.entry.ChwFollowUpScreen
import org.simple.clinic.chw.referral.Referral
import org.simple.clinic.chw.screening.entry.NewScreeningScreen
import org.simple.clinic.chw.ui.BannerTone
import org.simple.clinic.chw.ui.ChwBanner
import org.simple.clinic.chw.ui.ChwSectionCard
import org.simple.clinic.chw.ui.contactOutcomeLabel
import org.simple.clinic.chw.ui.formatDate
import org.simple.clinic.chw.ui.outcomeLabel
import org.simple.clinic.chw.ui.referralStatusLabel
import org.simple.clinic.chw.ui.taskTypeLabel
import org.simple.clinic.common.ui.components.FilledButton
import org.simple.clinic.common.ui.components.OutlinedButton
import org.simple.clinic.common.ui.components.TopAppBar
import org.simple.clinic.common.ui.theme.SimpleTheme
import org.simple.clinic.di.injector
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.User
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.toNullable
import org.simple.clinic.util.unsafeLazy
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class ChwPatientState(
    val patientName: String?,
    val myUserId: UUID,
    val role: ChwRole,
    val assignments: List<PatientChwAssignment>,
    val history: List<HistoryItem>,
    val referrals: List<Referral>,
    val openTasks: List<ChwFollowUpTask>
) {
  val myAssignment: PatientChwAssignment? get() = assignments.firstOrNull { it.chwUserId == myUserId && it.isCurrent }
  val assignedToOther: Boolean get() = myAssignment == null && assignments.any { it.isCurrent }
}

class ChwPatientScreen : Fragment() {

  @Inject lateinit var router: Router
  @Inject lateinit var chwRepository: ChwRepository
  @Inject lateinit var patientRepository: PatientRepository
  @Inject lateinit var bloodPressureRepository: BloodPressureRepository
  @Inject lateinit var chwSession: ChwSession
  @Inject lateinit var currentUser: dagger.Lazy<User>
  @Inject lateinit var schedulersProvider: SchedulersProvider

  private val screenKey by unsafeLazy { ScreenKey.key<Key>(this) }
  private val disposables = CompositeDisposable()
  private var state by mutableStateOf<ChwPatientState?>(null)

  override fun onAttach(context: Context) {
    super.onAttach(context)
    context.injector<Injector>().inject(this)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        SimpleTheme {
          ChwPatientContent(
              state = state,
              onBack = { router.pop() },
              onNewScreening = { router.push(NewScreeningScreen.Key(screenKey.patientUuid)) },
              onFollowUp = { taskId -> router.push(ChwFollowUpScreen.Key(screenKey.patientUuid, taskId)) }
          )
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val patientId = screenKey.patientUuid
    disposables.add(
        Observable.fromCallable { currentUser.get().uuid to chwSession.role() }
            .flatMap { (userId, role) ->
              Observables.combineLatest(
                  patientRepository.patient(patientId).map { it.toNullable()?.fullName.orEmpty() },
                  chwRepository.currentAssignmentsForPatientStream(patientId),
                  chwRepository.screeningsForPatient(patientId),
                  // CHWs see clinical BP values only as a limited, read-only summary.
                  bloodPressureRepository.newestMeasurementsForPatient(patientId, CLINIC_BP_LIMIT),
                  chwRepository.contactsForPatient(patientId),
                  chwRepository.referralsForPatient(patientId),
                  chwRepository.tasksForPatient(patientId)
              ) { name, assignments, screenings, bps, contacts, referrals, tasks ->
                ChwPatientState(
                    patientName = name.ifBlank { null },
                    myUserId = userId,
                    role = role,
                    assignments = assignments,
                    history = ChwPatientHistory.merge(screenings, bps, contacts),
                    referrals = referrals,
                    openTasks = tasks.filter { it.status == FollowUpTaskStatus.OPEN }.sortedBy { it.dueDate }
                )
              }
            }
            .subscribeOn(schedulersProvider.io())
            .observeOn(schedulersProvider.ui())
            .subscribe { state = it }
    )
  }

  override fun onDestroyView() {
    disposables.clear()
    super.onDestroyView()
  }

  interface Injector {
    fun inject(target: ChwPatientScreen)
  }

  @Parcelize
  data class Key(
      val patientUuid: UUID,
      override val analyticsName: String = "CHW Patient"
  ) : ScreenKey() {
    override fun instantiateFragment() = ChwPatientScreen()
  }

  companion object {
    private const val CLINIC_BP_LIMIT = 10
  }
}

@Composable
private fun ChwPatientContent(
    state: ChwPatientState?,
    onBack: () -> Unit,
    onNewScreening: () -> Unit,
    onFollowUp: (UUID?) -> Unit
) {
  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    TopAppBar(
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
        title = { Text(state?.patientName ?: stringResource(R.string.chw_patient_screen_title)) }
    )
    if (state == null) return@Column
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      val isChw = state.role == ChwRole.CHW
      if (!isChw) ChwBanner(stringResource(R.string.chw_role_not_chw), BannerTone.WARNING, Modifier.padding(8.dp))

      ChwSectionCard(null) {
        val my = state.myAssignment
        Text(
            when {
              my?.status == AssignmentStatus.ACTIVE -> stringResource(R.string.chw_assignment_active)
              my != null -> stringResource(R.string.chw_assignment_pending)
              state.assignedToOther -> stringResource(R.string.chw_assignment_other)
              else -> stringResource(R.string.chw_assignment_none)
            }
        )
        if (isChw && !state.assignedToOther) {
          FilledButton(onClick = onNewScreening, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("chw_new_screening")) {
            Text(stringResource(R.string.chw_new_screening))
          }
        }
        if (isChw && state.myAssignment != null) {
          OutlinedButton(onClick = { onFollowUp(state.openTasks.firstOrNull()?.id) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("chw_record_follow_up")) {
            Text(stringResource(R.string.chw_record_follow_up))
          }
        }
        Text(stringResource(R.string.chw_prescriptions_clinic_only), style = MaterialTheme.typography.caption, modifier = Modifier.padding(top = 8.dp))
      }

      if (state.referrals.isNotEmpty()) {
        ChwSectionCard(stringResource(R.string.chw_open_referrals_title)) {
          state.referrals.forEach { r ->
            Text(
                stringResource(if (r.priority == ReferralPriority.URGENT) R.string.chw_referral_priority_urgent else R.string.chw_referral_priority_routine) +
                    " · " + stringResource(R.string.chw_due_on, formatDate(r.dueDate)),
                style = MaterialTheme.typography.body1
            )
            Text(referralStatusLabel(r.status), style = MaterialTheme.typography.body2, color = if (r.status.isReceivedByClinic) MaterialTheme.colors.onSurface else MaterialTheme.colors.error)
            Divider(Modifier.padding(vertical = 4.dp))
          }
        }
      }

      if (state.openTasks.isNotEmpty()) {
        ChwSectionCard(stringResource(R.string.chw_open_tasks_title)) {
          state.openTasks.forEach { t ->
            Text("${taskTypeLabel(t.type)} · ${stringResource(R.string.chw_due_on, formatDate(t.dueDate))}")
          }
        }
      }

      ChwSectionCard(stringResource(R.string.chw_history_title)) {
        if (state.history.isEmpty()) Text(stringResource(R.string.chw_history_empty))
        state.history.forEach { item -> HistoryRow(item) }
      }
    }
  }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
  val date = formatDate(item.at.atZone(ZoneId.systemDefault()).toLocalDate())
  Column(Modifier.padding(vertical = 4.dp)) {
    when (item) {
      is HistoryItem.CommunityScreening -> {
        val e = item.episode
        Text(stringResource(R.string.chw_history_screening), style = MaterialTheme.typography.caption)
        val bp = if (e.secondSystolic != null && e.secondDiastolic != null) {
          stringResource(R.string.chw_bp_two_values, e.firstSystolic, e.firstDiastolic, e.secondSystolic, e.secondDiastolic)
        } else {
          stringResource(R.string.chw_bp_value, e.firstSystolic, e.firstDiastolic)
        }
        Text("$date · $bp · ${outcomeLabel(e.outcome)}")
        if (item.isPendingUpload) Text(stringResource(R.string.chw_sync_pending_label), color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
      }
      is HistoryItem.ClinicBloodPressure -> {
        Text(stringResource(R.string.chw_history_clinic_bp), style = MaterialTheme.typography.caption)
        Text("$date · ${stringResource(R.string.chw_bp_value, item.measurement.reading.systolic, item.measurement.reading.diastolic)}")
      }
      is HistoryItem.ChwContactAttempt -> {
        val c = item.contact
        Text(stringResource(R.string.chw_history_contact, contactOutcomeLabel(c.outcome)), style = MaterialTheme.typography.caption)
        val bp = if (c.systolic != null && c.diastolic != null) " · " + stringResource(R.string.chw_bp_value, c.systolic, c.diastolic) else ""
        Text("$date$bp")
        if (item.isPendingUpload) Text(stringResource(R.string.chw_sync_pending_label), color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
      }
    }
    Divider(Modifier.padding(top = 4.dp))
  }
}
