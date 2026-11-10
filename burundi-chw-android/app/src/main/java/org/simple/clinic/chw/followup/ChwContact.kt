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
import org.simple.clinic.chw.ContactMethod
import org.simple.clinic.chw.ContactOutcome
import org.simple.clinic.chw.ReportedMedicineAvailability
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A CHW follow-up contact attempt (successful or not).
 *
 * Medicine fields are *patient-reported* availability/adherence only. Prescriptions remain in
 * the upstream [org.simple.clinic.drugs.PrescribedDrug] table, which CHWs cannot edit.
 * The optional [note] must stay short and non-identifying; structured fields are preferred.
 */
@Parcelize
@Entity(
    tableName = "ChwContact",
    indices = [
      Index("patientId"),
      Index("taskId"),
      Index("syncStatus")
    ]
)
data class ChwContact(
    @PrimaryKey
    val id: UUID,
    val patientId: UUID,
    val chwUserId: UUID,
    val taskId: UUID?,
    val occurredAt: Instant,
    val method: ContactMethod,
    val outcome: ContactOutcome,
    val systolic: Int?,
    val diastolic: Int?,
    val dangerSigns: Set<DangerSign>,
    val medicineAvailability: ReportedMedicineAvailability,
    val sideEffectConcern: Boolean?,
    val nextDueDate: LocalDate?,
    val note: String?,
    @Embedded
    val timestamps: Timestamps,
    val syncStatus: SyncStatus
) : Parcelable {

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(records: List<ChwContact>)

    @Query("SELECT * FROM ChwContact WHERE id = :id")
    fun getOne(id: UUID): ChwContact?

    @Query("SELECT * FROM ChwContact WHERE patientId = :patientId AND deletedAt IS NULL ORDER BY occurredAt DESC")
    fun forPatient(patientId: UUID): Observable<List<ChwContact>>

    @Query("SELECT * FROM ChwContact WHERE patientId = :patientId AND deletedAt IS NULL ORDER BY occurredAt DESC")
    fun forPatientImmediate(patientId: UUID): List<ChwContact>

    @Query("SELECT COUNT(id) FROM ChwContact")
    fun recordCount(): Observable<Int>

    @Query("SELECT COUNT(id) FROM ChwContact WHERE syncStatus = :syncStatus")
    fun countWithStatus(syncStatus: SyncStatus): Observable<Int>

    @Query("SELECT * FROM ChwContact WHERE syncStatus = :syncStatus LIMIT :limit OFFSET :offset")
    fun recordsWithSyncStatusBatched(syncStatus: SyncStatus, limit: Int, offset: Int): List<ChwContact>

    @Query("SELECT id FROM ChwContact WHERE syncStatus = :syncStatus")
    fun recordIdsWithSyncStatus(syncStatus: SyncStatus): List<UUID>

    @Query("UPDATE ChwContact SET syncStatus = :to WHERE syncStatus = :from")
    fun updateSyncStatus(from: SyncStatus, to: SyncStatus)

    @Query("UPDATE ChwContact SET syncStatus = :to WHERE id IN (:ids)")
    fun updateSyncStatusForIds(ids: List<UUID>, to: SyncStatus)

    @Query("DELETE FROM ChwContact")
    fun clear()

    @Query("DELETE FROM ChwContact WHERE syncStatus = 'DONE' AND patientId NOT IN (:patientIdsInScope)")
    fun purgeSyncedOutsideScope(patientIdsInScope: List<UUID>): Int

    @Query("DELETE FROM ChwContact WHERE deletedAt IS NOT NULL AND syncStatus = 'DONE'")
    fun purgeDeleted(): Int
  }
}
