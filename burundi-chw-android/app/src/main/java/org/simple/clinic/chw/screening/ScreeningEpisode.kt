package org.simple.clinic.chw.screening

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
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.util.UUID

/**
 * A community screening encounter performed by a CHW.
 *
 * Deliberately separate from [org.simple.clinic.bp.BloodPressureMeasurement] and
 * [org.simple.clinic.medicalhistory.MedicalHistory]: a screening reading is NOT a clinical BP
 * visit and an elevated screening is NOT a hypertension diagnosis. Screening readings are never
 * written into the clinical BP table, so they cannot affect upstream clinical/reporting logic.
 *
 * [occurredAt] is when the screening happened on the device (recordedAt); [timestamps] are the
 * record bookkeeping times, and the server stamps its own received_at on sync.
 */
@Parcelize
@Entity(
    tableName = "ScreeningEpisode",
    indices = [
      Index("patientId"),
      Index("chwUserId"),
      Index("syncStatus")
    ]
)
data class ScreeningEpisode(
    @PrimaryKey
    val id: UUID,
    val patientId: UUID,
    val chwUserId: UUID,
    val communityAreaId: UUID?,
    val supervisingFacilityId: UUID,
    val occurredAt: Instant,
    val locationType: ScreeningLocationType,
    val consentVersion: String,
    val firstSystolic: Int,
    val firstDiastolic: Int,
    val secondSystolic: Int?,
    val secondDiastolic: Int?,
    val minutesBetweenReadings: Int?,
    val dangerSigns: Set<DangerSign>,
    val specialPopulation: SpecialPopulation,
    val outcome: ScreeningOutcome,
    /** Comma separated [ScreeningReason] codes, kept for audit of the automated triage. */
    val reasonCodes: String,
    val decisionRuleVersion: String,
    val decisionRuleApproved: Boolean,
    val referralId: UUID?,
    val followUpTaskId: UUID?,
    @Embedded
    val timestamps: Timestamps,
    val syncStatus: SyncStatus
) : Parcelable {

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(records: List<ScreeningEpisode>)

    @Query("SELECT * FROM ScreeningEpisode WHERE id = :id")
    fun getOne(id: UUID): ScreeningEpisode?

    @Query("""
      SELECT * FROM ScreeningEpisode
      WHERE patientId = :patientId AND deletedAt IS NULL
      ORDER BY occurredAt DESC
    """)
    fun forPatient(patientId: UUID): Observable<List<ScreeningEpisode>>

    @Query("SELECT COUNT(id) FROM ScreeningEpisode")
    fun recordCount(): Observable<Int>

    @Query("SELECT COUNT(id) FROM ScreeningEpisode WHERE syncStatus = :syncStatus")
    fun countWithStatus(syncStatus: SyncStatus): Observable<Int>

    @Query("SELECT * FROM ScreeningEpisode WHERE syncStatus = :syncStatus LIMIT :limit OFFSET :offset")
    fun recordsWithSyncStatusBatched(syncStatus: SyncStatus, limit: Int, offset: Int): List<ScreeningEpisode>

    @Query("SELECT id FROM ScreeningEpisode WHERE syncStatus = :syncStatus")
    fun recordIdsWithSyncStatus(syncStatus: SyncStatus): List<UUID>

    @Query("UPDATE ScreeningEpisode SET syncStatus = :to WHERE syncStatus = :from")
    fun updateSyncStatus(from: SyncStatus, to: SyncStatus)

    @Query("UPDATE ScreeningEpisode SET syncStatus = :to WHERE id IN (:ids)")
    fun updateSyncStatusForIds(ids: List<UUID>, to: SyncStatus)

    @Query("DELETE FROM ScreeningEpisode")
    fun clear()

    /**
     * Removes synced screenings for patients no longer in the device's scope.
     * Records still PENDING upload are never deleted.
     */
    @Query("""
      DELETE FROM ScreeningEpisode
      WHERE syncStatus = 'DONE'
      AND patientId NOT IN (:patientIdsInScope)
    """)
    fun purgeSyncedOutsideScope(patientIdsInScope: List<UUID>): Int

    @Query("DELETE FROM ScreeningEpisode WHERE deletedAt IS NOT NULL AND syncStatus = 'DONE'")
    fun purgeDeleted(): Int
  }
}
