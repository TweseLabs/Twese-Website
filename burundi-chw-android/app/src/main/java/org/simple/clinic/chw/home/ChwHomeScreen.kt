package org.simple.clinic.chw.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import io.reactivex.subjects.BehaviorSubject
import kotlinx.parcelize.Parcelize
import org.simple.clinic.R
import org.simple.clinic.chw.ChwActions
import org.simple.clinic.chw.ChwRepository
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.access.ChwSession
import org.simple.clinic.chw.patient.ChwPatientScreen
import org.simple.clinic.chw.queue.ChwWorkQueue
import org.simple.clinic.chw.queue.WorkQueueItem
import org.simple.clinic.chw.queue.WorkQueueSection
import org.simple.clinic.chw.ui.BannerTone
import org.simple.clinic.chw.ui.ChwBanner
import org.simple.clinic.chw.ui.ChwSectionCard
import org.simple.clinic.chw.ui.RadioGroup
import org.simple.clinic.chw.ui.formatDate
import org.simple.clinic.chw.ui.referralStatusLabel
import org.simple.clinic.chw.ui.taskTypeLabel
import org.simple.clinic.common.ui.components.OutlinedButton
import org.simple.clinic.common.ui.theme.SimpleTheme
import org.simple.clinic.di.injector
import org.simple.clinic.feature.Feature
import org.simple.clinic.feature.Features
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.scheduler.SchedulersProvider
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ChwHomeState(
    val role: ChwRole,
    val isDemoRole: Boolean,
    val demoAllowed: Boolean,
    val protocolApproved: Boolean,
    val areaName: String?,
    val clinicName: String,
    val hasAssignment: Boolean,
    val pendingUploads: Int,
    val serverSyncEnabled: Boolean,
    val assignedPatients: Int,
    val sections: Map<WorkQueueSection, List<WorkQueueItem>>,
    val names: Map<UUID, String>
)

class ChwHomeScreen : Fragment() {

  @Inject lateinit var router: Router
  @Inject lateinit var chwActions: ChwActions
  @Inject lateinit var chwRepository: ChwRepository
  @Inject lateinit var chwSession: ChwSession
  @Inject lateinit var patientRepository: PatientRepository
  @Inject lateinit var features: Features
  @Inject lateinit var userClock: UserClock
  @Inject lateinit var schedulersProvider: SchedulersProvider

  private val disposables = CompositeDisposable()
  private val refresh = BehaviorSubject.createDefault(Unit)
  private var state by mutableStateOf<ChwHomeState?>(null)

  override fun onAttach(context: Context) {
    super.onAttach(context)
    context.injector<Injector>().inject(this)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        SimpleTheme {
          ChwHomeContent(
              state = state,
              onOpenPatient = { router.push(ChwPatientScreen.Key(it)) },
              onDemoRole = { role -> runIo { chwSession.setDemoRole(role) } },
              onSeedDemoArea = { runIo { chwActions.seedDemoAreaIfAllowed() } }
          )
        }
      }
    }
  }

  private fun runIo(block: () -> Unit) {
    disposables.add(
        io.reactivex.Completable.fromAction(block)
            .subscribeOn(schedulersProvider.io())
            .observeOn(schedulersProvider.ui())
            .subscribe { refresh.onNext(Unit) }
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    disposables.add(
        refresh
            .observeOn(schedulersProvider.io())
            .switchMap {
              val ctx = chwActions.context()
              val clinicName = chwActions.currentFacilityName()
              Observables.combineLatest(
                  chwRepository.openTasksForChw(ctx.user.uuid),
                  chwRepository.openReferralsCreatedBy(ctx.user.uuid),
                  chwRepository.assignedPatientIds(ctx.user.uuid),
                  chwRepository.pendingUploadCount()
              ) { tasks, referrals, assigned, pending ->
                val sections = ChwWorkQueue.build(LocalDate.now(userClock), tasks, referrals)
                val ids = sections.values.flatten().map { it.patientId }.toSet()
                ChwHomeState(
                    role = ctx.role,
                    isDemoRole = ctx.isDemoRole,
                    demoAllowed = chwSession.isDemoRoleAllowed,
                    protocolApproved = ctx.protocol.approved,
                    areaName = ctx.area?.displayName,
                    clinicName = clinicName,
                    hasAssignment = ctx.assignment != null,
                    pendingUploads = pending,
                    serverSyncEnabled = features.isEnabled(Feature.BurundiChwServerSync),
                    assignedPatients = assigned.size,
                    sections = sections,
                    names = ids.associateWith { patientRepository.patientImmediate(it)?.fullName.orEmpty() }
                )
              }
            }
            .observeOn(schedulersProvider.ui())
            .subscribe { state = it }
    )
  }

  override fun onDestroyView() {
    disposables.clear()
    super.onDestroyView()
  }

  interface Injector {
    fun inject(target: ChwHomeScreen)
  }

  @Parcelize
  data class Key(
      override val analyticsName: String = "CHW Home"
  ) : ScreenKey() {
    override fun instantiateFragment() = ChwHomeScreen()
  }
}

private val sectionOrder = listOf(
    WorkQueueSection.URGENT_UNRESOLVED to R.string.chw_section_urgent,
    WorkQueueSection.OVERDUE to R.string.chw_section_overdue,
    WorkQueueSection.DUE_TODAY to R.string.chw_section_due_today,
    WorkQueueSection.DUE_THIS_WEEK to R.string.chw_section_due_this_week,
    WorkQueueSection.AWAITING_CLINICAL_CONFIRMATION to R.string.chw_section_awaiting_confirmation,
    WorkQueueSection.ACTIVE_FOLLOW_UP to R.string.chw_section_active_follow_up
)

@Composable
private fun ChwHomeContent(
    state: ChwHomeState?,
    onOpenPatient: (UUID) -> Unit,
    onDemoRole: (ChwRole?) -> Unit,
    onSeedDemoArea: () -> Unit
) {
  if (state == null) return
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (state.isDemoRole) ChwBanner(stringResource(R.string.chw_demo_role_banner, state.role.code), BannerTone.WARNING)
    if (!state.protocolApproved) ChwBanner(stringResource(R.string.chw_draft_protocol_banner), BannerTone.WARNING)

    // Sync honesty: "saved on phone" is never presented as "received by clinic".
    if (!state.serverSyncEnabled) ChwBanner(stringResource(R.string.chw_server_sync_unavailable), BannerTone.WARNING)
    ChwBanner(
        if (state.pendingUploads > 0) stringResource(R.string.chw_pending_upload_count, state.pendingUploads) else stringResource(R.string.chw_all_saved_records_sent),
        if (state.pendingUploads > 0) BannerTone.WARNING else BannerTone.INFO,
        Modifier.testTag("chw_pending_uploads")
    )

    if (state.role != ChwRole.CHW) {
      ChwBanner(stringResource(R.string.chw_role_not_chw), BannerTone.INFO, Modifier.padding(8.dp))
    } else {
      ChwSectionCard(null) {
        if (state.hasAssignment) {
          Text(stringResource(R.string.chw_area_and_clinic, state.areaName.orEmpty(), state.clinicName))
        } else {
          Text(stringResource(R.string.chw_no_area_assignment), style = MaterialTheme.typography.body2)
          Text(stringResource(R.string.chw_supervising_clinic, state.clinicName))
        }
        Text(stringResource(R.string.chw_assigned_patients_count, state.assignedPatients), style = MaterialTheme.typography.subtitle2)
        Text(stringResource(R.string.chw_find_or_register_patient), style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.chw_find_or_register_hint), style = MaterialTheme.typography.body2)
      }

      if (state.sections.isEmpty()) {
        ChwSectionCard(null) { Text(stringResource(R.string.chw_queue_empty)) }
      }
      sectionOrder.forEach { (section, title) ->
        val items = state.sections[section].orEmpty()
        if (items.isNotEmpty()) {
          ChwSectionCard("${stringResource(title)} (${items.size})", Modifier.testTag("chw_section_${section.name}")) {
            items.forEach { item -> QueueRow(item, state.names[item.patientId], onOpenPatient) }
          }
        }
      }
    }

    if (state.demoAllowed) {
      ChwSectionCard(stringResource(R.string.chw_demo_role_title)) {
        RadioGroup(
            listOf(
                ChwRole.CHW to stringResource(R.string.chw_role_chw),
                ChwRole.CLINICIAN to stringResource(R.string.chw_role_clinician),
                ChwRole.FIELD_COORDINATOR to stringResource(R.string.chw_role_field_coordinator),
                ChwRole.PROGRAM_ADMIN to stringResource(R.string.chw_role_program_admin),
                ChwRole.LEGACY_CLINIC_USER to stringResource(R.string.chw_role_legacy)
            ),
            state.role
        ) { onDemoRole(if (it == ChwRole.LEGACY_CLINIC_USER) null else it) }
        if (!state.hasAssignment) {
          OutlinedButton(onClick = onSeedDemoArea, modifier = Modifier.fillMaxWidth().testTag("chw_seed_demo_area")) {
            Text(stringResource(R.string.chw_demo_seed_area))
          }
        }
      }
    }
  }
}

@Composable
private fun QueueRow(item: WorkQueueItem, name: String?, onOpen: (UUID) -> Unit) {
  Column(Modifier.fillMaxWidth().clickable { onOpen(item.patientId) }.padding(vertical = 6.dp)) {
    Text(name?.ifBlank { null } ?: stringResource(R.string.chw_unknown_patient), style = MaterialTheme.typography.body1)
    val what = item.referralStatus?.let { referralStatusLabel(it) } ?: taskTypeLabel(item.taskType)
    Text("$what · ${stringResource(R.string.chw_due_on, formatDate(item.dueDate))}", style = MaterialTheme.typography.body2)
  }
  Divider()
}
