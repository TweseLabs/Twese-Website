package org.simple.clinic.chw.area

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
import org.simple.clinic.chw.AssignmentEndReason
import org.simple.clinic.chw.AssignmentStatus
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.util.UUID

/**
 * Links a patient to the CHW responsible for community follow-up.
 *
 * This is intentionally a separate table: the upstream Patient.registeredFacilityId /
 * assignedFacilityId fields keep their clinical meaning and are never repurposed.
 *
 * Reassignment never edits a row in place: the old row is ENDED (with [endReason]) and a new row
 * references it via [previousAssignmentId], preserving history for audit.
 * A CHW-created row starts as PENDING_SERVER_CONFIRMATION; only the server activates it.
 */
@Parcelize
@Entity(
    tableName = "PatientChwAssignment",
    indices = [
      Index("patientId"),
      Index("chwUserId", "status"),
      Index("syncStatus")
    ]
)
data class PatientChwAssignment(
    @PrimaryKey
    val id: UUID,
    val patientId: UUID,
    val chwUserId: UUID,
    val communityAreaId: UUID?,
    val assignedAt: Instant,
    val status: AssignmentStatus,
    val endReason: AssignmentEndReason?,
    val previousAssignmentId: UUID?,
    @Embedded
    val timestamps: Timestamps,
    val syncStatus: SyncStatus
) : Parcelable {

  val isCurrent: Boolean
    get() = timestamps.deletedAt == null &&
        (status == AssignmentStatus.ACTIVE || status == AssignmentStatus.PENDING_SERVER_CONFIRMATION)

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(records: List<PatientChwAssignment>)

    @Query("""
      SELECT * FROM PatientChwAssignment
      WHERE patientId = :patientId AND deletedAt IS NULL
      AND status IN ('active', 'pending_server_confirmation')
      ORDER BY assignedAt DESC
    """)
    fun currentForPatientImmediate(patientId: UUID): List<PatientChwAssignment>

    @Query("""
      SELECT * FROM PatientChwAssignment
      WHERE patientId = :patientId AND deletedAt IS NULL
      AND status IN ('active', 'pending_server_confirmation')
      ORDER BY assignedAt DESC
    """)
    fun currentForPatient(patientId: UUID): Observable<List<PatientChwAssignment>>

    @Query("""
      SELECT DISTINCT patientId FROM PatientChwAssignment
      WHERE chwUserId = :chwUserId AND deletedAt IS NULL
      AND status IN ('active', 'pending_server_confirmation')
    """)
    fun assignedPatientIds(chwUserId: UUID): Observable<List<UUID>>

    @Query("""
      SELECT DISTINCT patientId FROM PatientChwAssignment
      WHERE chwUserId = :chwUserId AND deletedAt IS NULL
      AND status IN ('active', 'pending_server_confirmation')
    """)
    fun assignedPatientIdsImmediate(chwUserId: UUID): List<UUID>

    @Query("SELECT COUNT(id) FROM PatientChwAssignment")
    fun recordCount(): Observable<Int>

    @Query("SELECT COUNT(id) FROM PatientChwAssignment WHERE syncStatus = :syncStatus")
    fun countWithStatus(syncStatus: SyncStatus): Observable<Int>

    @Query("SELECT * FROM PatientChwAssignment WHERE syncStatus = :syncStatus LIMIT :limit OFFSET :offset")
    fun recordsWithSyncStatusBatched(syncStatus: SyncStatus, limit: Int, offset: Int): List<PatientChwAssignment>

    @Query("SELECT id FROM PatientChwAssignment WHERE syncStatus = :syncStatus")
    fun recordIdsWithSyncStatus(syncStatus: SyncStatus): List<UUID>

    @Query("UPDATE PatientChwAssignment SET syncStatus = :to WHERE syncStatus = :from")
    fun updateSyncStatus(from: SyncStatus, to: SyncStatus)

    @Query("UPDATE PatientChwAssignment SET syncStatus = :to WHERE id IN (:ids)")
    fun updateSyncStatusForIds(ids: List<UUID>, to: SyncStatus)

    @Query("DELETE FROM PatientChwAssignment")
    fun clear()

    @Query("DELETE FROM PatientChwAssignment WHERE syncStatus = 'DONE' AND patientId NOT IN (:patientIdsInScope)")
    fun purgeSyncedOutsideScope(patientIdsInScope: List<UUID>): Int
  }
}
