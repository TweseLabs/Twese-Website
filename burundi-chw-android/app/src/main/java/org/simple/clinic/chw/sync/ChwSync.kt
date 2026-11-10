package org.simple.clinic.chw.sync

import android.content.SharedPreferences
import org.simple.clinic.AppDatabase
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.SharedPreferencesMode
import org.simple.clinic.storage.SharedPreferencesMode.Mode.Default
import org.simple.clinic.sync.ModelSync
import org.simple.clinic.sync.SyncConfig
import org.simple.clinic.sync.SyncConfigType
import org.simple.clinic.util.read
import retrofit2.Call
import javax.inject.Inject

/**
 * Syncs CHW module records. Registered in [org.simple.clinic.sync.SyncModule] ONLY when
 * [org.simple.clinic.feature.Feature.BurundiChwServerSync] is enabled (off by default because
 * the server endpoints do not exist yet). While disabled, CHW records stay PENDING on the device
 * and the UI says "saved on this phone – not yet sent".
 */
class ChwSync @Inject constructor(
    private val database: AppDatabase,
    private val api: ChwSyncApi,
    @SharedPreferencesMode(Default) private val sharedPreferences: SharedPreferences,
    @SyncConfigType(SyncConfigType.Type.Frequent) private val config: SyncConfig
) : ModelSync {

  override val name: String = "BurundiChw"

  override val requiresSyncApprovedUser: Boolean = true

  override fun push() {
    // Order matters for server-side referential checks: assignment → screening → referral → task → contact.
    channels().forEach { ChwSyncEngine.push(it, config.pushBatchSize) }
  }

  override fun pull() {
    channels().forEach { ChwSyncEngine.pull(it, config.pullBatchSize) }
  }

  private fun <T> Call<T>.body(): T = execute().read()!!

  private fun token(key: String): () -> String? = { sharedPreferences.getString(key, null) }
  private fun saveToken(key: String): (String) -> Unit = { sharedPreferences.edit().putString(key, it).apply() }

  private fun channels(): List<ChwChannel<*, *>> {
    val areaDao = database.communityAreaDao()
    val chwAssignmentDao = database.chwAssignmentDao()
    val patientAssignmentDao = database.patientChwAssignmentDao()
    val screeningDao = database.screeningEpisodeDao()
    val referralDao = database.chwReferralDao()
    val taskDao = database.chwFollowUpTaskDao()
    val contactDao = database.chwContactDao()

    return listOf(
        ChwChannel(
            name = "CommunityArea",
            firstPendingBatch = { emptyList<Unit>() },
            idOf = { error("pull only") },
            toPayload = { error("pull only") },
            pushCall = null,
            markDone = {},
            markInvalid = {},
            pendingIds = { emptyList() },
            pullCall = { api.pullAreas(config.pullBatchSize, it).body() },
            payloadId = CommunityAreaPayload::id,
            saveFromServer = { payloads -> areaDao.save(payloads.map { it.toDatabaseModel() }) },
            readToken = token("burundi_pull_token_community_areas"),
            writeToken = saveToken("burundi_pull_token_community_areas")
        ),
        ChwChannel(
            name = "ChwAssignment",
            firstPendingBatch = { emptyList<Unit>() },
            idOf = { error("pull only") },
            toPayload = { error("pull only") },
            pushCall = null,
            markDone = {},
            markInvalid = {},
            pendingIds = { emptyList() },
            pullCall = { api.pullChwAssignments(config.pullBatchSize, it).body() },
            payloadId = ChwAssignmentPayload::id,
            saveFromServer = { payloads -> chwAssignmentDao.save(payloads.map { it.toDatabaseModel() }) },
            readToken = token("burundi_pull_token_chw_assignments"),
            writeToken = saveToken("burundi_pull_token_chw_assignments")
        ),
        ChwChannel(
            name = "PatientChwAssignment",
            firstPendingBatch = { patientAssignmentDao.recordsWithSyncStatusBatched(SyncStatus.PENDING, it, 0) },
            idOf = { it.id },
            toPayload = PatientChwAssignmentPayload::from,
            pushCall = { api.pushPatientAssignments(ChwPushRequest(records = it)).body() },
            markDone = { patientAssignmentDao.updateSyncStatusForIds(it, SyncStatus.DONE) },
            markInvalid = { patientAssignmentDao.updateSyncStatusForIds(it, SyncStatus.INVALID) },
            pendingIds = { patientAssignmentDao.recordIdsWithSyncStatus(SyncStatus.PENDING) },
            pullCall = { api.pullPatientAssignments(config.pullBatchSize, it).body() },
            payloadId = PatientChwAssignmentPayload::id,
            saveFromServer = { payloads -> patientAssignmentDao.save(payloads.map { it.toDatabaseModel(SyncStatus.DONE) }) },
            readToken = token("burundi_pull_token_patient_chw_assignments"),
            writeToken = saveToken("burundi_pull_token_patient_chw_assignments")
        ),
        ChwChannel(
            name = "ScreeningEpisode",
            firstPendingBatch = { screeningDao.recordsWithSyncStatusBatched(SyncStatus.PENDING, it, 0) },
            idOf = { it.id },
            toPayload = ScreeningEpisodePayload::from,
            pushCall = { api.pushScreenings(ChwPushRequest(records = it)).body() },
            markDone = { screeningDao.updateSyncStatusForIds(it, SyncStatus.DONE) },
            markInvalid = { screeningDao.updateSyncStatusForIds(it, SyncStatus.INVALID) },
            pendingIds = { screeningDao.recordIdsWithSyncStatus(SyncStatus.PENDING) },
            pullCall = { api.pullScreenings(config.pullBatchSize, it).body() },
            payloadId = ScreeningEpisodePayload::id,
            saveFromServer = { payloads -> screeningDao.save(payloads.map { it.toDatabaseModel(SyncStatus.DONE) }) },
            readToken = token("burundi_pull_token_screening_episodes"),
            writeToken = saveToken("burundi_pull_token_screening_episodes")
        ),
        ChwChannel(
            name = "Referral",
            firstPendingBatch = { referralDao.recordsWithSyncStatusBatched(SyncStatus.PENDING, it, 0) },
            idOf = { it.id },
            toPayload = ReferralPayload::from,
            pushCall = { api.pushReferrals(ChwPushRequest(records = it)).body() },
            markDone = { referralDao.updateSyncStatusForIds(it, SyncStatus.DONE) },
            markInvalid = { referralDao.updateSyncStatusForIds(it, SyncStatus.INVALID) },
            pendingIds = { referralDao.recordIdsWithSyncStatus(SyncStatus.PENDING) },
            pullCall = { api.pullReferrals(config.pullBatchSize, it).body() },
            payloadId = ReferralPayload::id,
            saveFromServer = { payloads -> referralDao.save(payloads.map { it.toDatabaseModel(SyncStatus.DONE) }) },
            readToken = token("burundi_pull_token_referrals"),
            writeToken = saveToken("burundi_pull_token_referrals")
        ),
        ChwChannel(
            name = "ChwFollowUpTask",
            firstPendingBatch = { taskDao.recordsWithSyncStatusBatched(SyncStatus.PENDING, it, 0) },
            idOf = { it.id },
            toPayload = ChwFollowUpTaskPayload::from,
            pushCall = { api.pushTasks(ChwPushRequest(records = it)).body() },
            markDone = { taskDao.updateSyncStatusForIds(it, SyncStatus.DONE) },
            markInvalid = { taskDao.updateSyncStatusForIds(it, SyncStatus.INVALID) },
            pendingIds = { taskDao.recordIdsWithSyncStatus(SyncStatus.PENDING) },
            pullCall = { api.pullTasks(config.pullBatchSize, it).body() },
            payloadId = ChwFollowUpTaskPayload::id,
            saveFromServer = { payloads -> taskDao.save(payloads.map { it.toDatabaseModel(SyncStatus.DONE) }) },
            readToken = token("burundi_pull_token_chw_follow_up_tasks"),
            writeToken = saveToken("burundi_pull_token_chw_follow_up_tasks")
        ),
        ChwChannel(
            name = "ChwContact",
            firstPendingBatch = { contactDao.recordsWithSyncStatusBatched(SyncStatus.PENDING, it, 0) },
            idOf = { it.id },
            toPayload = ChwContactPayload::from,
            pushCall = { api.pushContacts(ChwPushRequest(records = it)).body() },
            markDone = { contactDao.updateSyncStatusForIds(it, SyncStatus.DONE) },
            markInvalid = { contactDao.updateSyncStatusForIds(it, SyncStatus.INVALID) },
            pendingIds = { contactDao.recordIdsWithSyncStatus(SyncStatus.PENDING) },
            pullCall = { api.pullContacts(config.pullBatchSize, it).body() },
            payloadId = ChwContactPayload::id,
            saveFromServer = { payloads -> contactDao.save(payloads.map { it.toDatabaseModel(SyncStatus.DONE) }) },
            readToken = token("burundi_pull_token_chw_contacts"),
            writeToken = saveToken("burundi_pull_token_chw_contacts")
        )
    )
  }
}
