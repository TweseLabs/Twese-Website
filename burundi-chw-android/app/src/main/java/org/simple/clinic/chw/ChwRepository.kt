package org.simple.clinic.chw

import io.reactivex.Observable
import org.simple.clinic.AppDatabase
import org.simple.clinic.chw.area.ChwAssignment
import org.simple.clinic.chw.area.CommunityArea
import org.simple.clinic.chw.area.PatientChwAssignment
import org.simple.clinic.chw.followup.ChwContact
import org.simple.clinic.chw.followup.ChwFollowUpTask
import org.simple.clinic.chw.followup.FollowUpPlan
import org.simple.clinic.chw.referral.Referral
import org.simple.clinic.chw.screening.ScreeningEpisode
import org.simple.clinic.chw.screening.ScreeningPlan
import org.simple.clinic.patient.SyncStatus
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Local storage facade for the CHW module. All multi-record writes are atomic. */
class ChwRepository @Inject constructor(
    private val database: AppDatabase
) {

  private val screeningDao get() = database.screeningEpisodeDao()
  private val referralDao get() = database.chwReferralDao()
  private val taskDao get() = database.chwFollowUpTaskDao()
  private val contactDao get() = database.chwContactDao()
  private val patientAssignmentDao get() = database.patientChwAssignmentDao()
  private val chwAssignmentDao get() = database.chwAssignmentDao()
  private val areaDao get() = database.communityAreaDao()

  fun saveScreening(plan: ScreeningPlan) {
    database.runInTransaction {
      plan.newPatientAssignment?.let { patientAssignmentDao.save(listOf(it)) }
      plan.referral?.let { referralDao.save(listOf(it)) }
      taskDao.save(plan.tasksToComplete + plan.newTask)
      screeningDao.save(listOf(plan.episode))
    }
  }

  fun saveFollowUp(plan: FollowUpPlan) {
    database.runInTransaction {
      plan.urgentReferral?.let { referralDao.save(listOf(it)) }
      taskDao.save(plan.updatedTasks + listOfNotNull(plan.newTask))
      contactDao.save(listOf(plan.contact))
    }
  }

  fun currentAssignmentsForPatient(patientId: UUID): List<PatientChwAssignment> =
      patientAssignmentDao.currentForPatientImmediate(patientId)

  fun currentAssignmentsForPatientStream(patientId: UUID): Observable<List<PatientChwAssignment>> =
      patientAssignmentDao.currentForPatient(patientId)

  fun openTasksForPatient(patientId: UUID): List<ChwFollowUpTask> = taskDao.openTasksForPatientImmediate(patientId)

  fun openTasksForChw(chwUserId: UUID): Observable<List<ChwFollowUpTask>> = taskDao.openTasksFor(chwUserId)

  fun openReferralsCreatedBy(chwUserId: UUID): Observable<List<Referral>> = referralDao.openReferralsCreatedBy(chwUserId)

  fun assignedPatientIds(chwUserId: UUID): Observable<List<UUID>> = patientAssignmentDao.assignedPatientIds(chwUserId)

  fun screeningsForPatient(patientId: UUID): Observable<List<ScreeningEpisode>> = screeningDao.forPatient(patientId)

  fun referralsForPatient(patientId: UUID): Observable<List<Referral>> = referralDao.forPatient(patientId)

  fun contactsForPatient(patientId: UUID): Observable<List<ChwContact>> = contactDao.forPatient(patientId)

  fun tasksForPatient(patientId: UUID): Observable<List<ChwFollowUpTask>> = taskDao.forPatient(patientId)

  fun activeChwAssignment(chwUserId: UUID, today: LocalDate): ChwAssignment? =
      chwAssignmentDao.forUserImmediate(chwUserId).firstOrNull { it.isActiveOn(today) }

  fun area(id: UUID): CommunityArea? = areaDao.getOne(id)

  /** Count of CHW records saved on this phone that have not been uploaded. */
  fun pendingUploadCount(): Observable<Int> = Observable.combineLatest(
      listOf(
          screeningDao.countWithStatus(SyncStatus.PENDING),
          referralDao.countWithStatus(SyncStatus.PENDING),
          taskDao.countWithStatus(SyncStatus.PENDING),
          contactDao.countWithStatus(SyncStatus.PENDING),
          patientAssignmentDao.countWithStatus(SyncStatus.PENDING)
      )
  ) { counts -> counts.sumOf { it as Int } }

  /**
   * Secure local purge after reassignment / revocation / transfer out of scope.
   * Only already-uploaded (DONE) records are removed; unsynced work is never discarded.
   */
  fun purgeOutsideScope(chwUserId: UUID): Int {
    return database.runInTransaction<Int> {
      val inScope = patientAssignmentDao.assignedPatientIdsImmediate(chwUserId)
      screeningDao.purgeSyncedOutsideScope(inScope) +
          referralDao.purgeSyncedOutsideScope(inScope) +
          taskDao.purgeSyncedOutsideScope(inScope) +
          contactDao.purgeSyncedOutsideScope(inScope) +
          patientAssignmentDao.purgeSyncedOutsideScope(inScope)
    }
  }

  /** Debug-only synthetic setup; see ChwDemoSeeder. */
  fun saveAreaAndAssignment(area: CommunityArea, assignment: ChwAssignment) {
    database.runInTransaction {
      areaDao.save(listOf(area))
      chwAssignmentDao.save(listOf(assignment))
    }
  }
}
