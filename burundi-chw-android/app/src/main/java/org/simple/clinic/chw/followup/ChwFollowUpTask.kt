package org.simple.clinic.chw.followup

import android.os.Parcelable
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import io.reactivex.Observable
import kotlinx.parcelize.Parcelize
import org.simple.clinic.chw.FollowUpTaskStatus
import org.simple.clinic.chw.FollowUpTaskType
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.LocalDate
import java.util.UUID

/**
 * A CHW work item (repeat screening, checking whether a referral was completed, routine
 * follow-up). Kept separate from clinic [org.simple.clinic.overdue.Appointment]s so CHW contacts
 * never appear as missed *clinic* visits in the upstream overdue list, and so the existing
 * appointment sync contract is not changed.
 */
@Parcelize
@Entity(
    tableName = "ChwFollowUpTask",
    indices = [
      Index("patientId"),
      Index("chwUserId", "status", "dueDate"),
      Index("syncStatus")
    ]
)
data class ChwFollowUpTask(
    @PrimaryKey
    val id: UUID,
    val patientId: UUID,
    val chwUserId: UUID,
    val communityAreaId: UUID?,
    val type: FollowUpTaskType,
    val dueDate: LocalDate,
    val status: FollowUpTaskStatus,
    val screeningEpisodeId: UUID?,
    val referralId: UUID?,
    val completedByContactId: UUID?,
    /** Number of times this task has been rescheduled (for quality monitoring, not incentives). */
    val rescheduleCount: Int,
    @Embedded
    val timestamps: Timestamps,
    val syncStatus: SyncStatus
) : Parcelable {

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(records: List<ChwFollowUpTask>)

    @Query("SELECT * FROM ChwFollowUpTask WHERE id = :id")
    fun getOne(id: UUID): ChwFollowUpTask?

    @Query("""
      SELECT * FROM ChwFollowUpTask
      WHERE chwUserId = :chwUserId AND status = 'open' AND deletedAt IS NULL
      ORDER BY dueDate ASC
    """)
    fun openTasksFor(chwUserId: UUID): Observable<List<ChwFollowUpTask>>

    @Query("""
      SELECT * FROM ChwFollowUpTask
      WHERE patientId = :patientId AND deletedAt IS NULL
      ORDER BY dueDate DESC
    """)
    fun forPatient(patientId: UUID): Observable<List<ChwFollowUpTask>>

    @Query("""
      SELECT * FROM ChwFollowUpTask
      WHERE patientId = :patientId AND status = 'open' AND deletedAt IS NULL
      ORDER BY dueDate ASC
    """)
    fun openTasksForPatientImmediate(patientId: UUID): List<ChwFollowUpTask>

    @Query("SELECT COUNT(id) FROM ChwFollowUpTask")
    fun recordCount(): Observable<Int>

    @Query("SELECT COUNT(id) FROM ChwFollowUpTask WHERE syncStatus = :syncStatus")
    fun countWithStatus(syncStatus: SyncStatus): Observable<Int>

    @Query("SELECT * FROM ChwFollowUpTask WHERE syncStatus = :syncStatus LIMIT :limit OFFSET :offset")
    fun recordsWithSyncStatusBatched(syncStatus: SyncStatus, limit: Int, offset: Int): List<ChwFollowUpTask>

    @Query("SELECT id FROM ChwFollowUpTask WHERE syncStatus = :syncStatus")
    fun recordIdsWithSyncStatus(syncStatus: SyncStatus): List<UUID>

    @Query("UPDATE ChwFollowUpTask SET syncStatus = :to WHERE syncStatus = :from")
    fun updateSyncStatus(from: SyncStatus, to: SyncStatus)

    @Query("UPDATE ChwFollowUpTask SET syncStatus = :to WHERE id IN (:ids)")
    fun updateSyncStatusForIds(ids: List<UUID>, to: SyncStatus)

    @Query("DELETE FROM ChwFollowUpTask")
    fun clear()

    @Query("DELETE FROM ChwFollowUpTask WHERE syncStatus = 'DONE' AND patientId NOT IN (:patientIdsInScope)")
    fun purgeSyncedOutsideScope(patientIdsInScope: List<UUID>): Int

    @Query("DELETE FROM ChwFollowUpTask WHERE deletedAt IS NOT NULL AND syncStatus = 'DONE'")
    fun purgeDeleted(): Int
  }
}
