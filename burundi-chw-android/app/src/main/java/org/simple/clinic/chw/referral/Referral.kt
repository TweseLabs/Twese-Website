package org.simple.clinic.chw.referral

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
import org.simple.clinic.chw.ReferralPriority
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * CHW → clinic referral. Status history and acknowledgement timestamps are authoritative on the
 * server; the device never marks a referral as received by a clinic on its own.
 */
@Parcelize
@Entity(
    tableName = "ChwReferral",
    indices = [
      Index("patientId"),
      Index("destinationFacilityId"),
      Index("sourceUserId"),
      Index("syncStatus")
    ]
)
data class Referral(
    @PrimaryKey
    val id: UUID,
    val patientId: UUID,
    val screeningEpisodeId: UUID?,
    val sourceUserId: UUID,
    val originatingAreaId: UUID?,
    val destinationFacilityId: UUID,
    val priority: ReferralPriority,
    /** Comma separated reason codes (screening outcome + reasons). */
    val reasonCodes: String,
    val status: ReferralStatus,
    val requestedAt: Instant,
    val dueDate: LocalDate,
    val acknowledgedAt: Instant?,
    val completedAt: Instant?,
    val clinicianUserId: UUID?,
    val outcomeCode: String?,
    /** Clinician-approved, structured instruction returned to the CHW (no free-text PHI). */
    val nextActionCode: String?,
    @Embedded
    val timestamps: Timestamps,
    val syncStatus: SyncStatus
) : Parcelable {

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(records: List<Referral>)

    @Query("SELECT * FROM ChwReferral WHERE id = :id")
    fun getOne(id: UUID): Referral?

    @Query("SELECT * FROM ChwReferral WHERE patientId = :patientId AND deletedAt IS NULL ORDER BY requestedAt DESC")
    fun forPatient(patientId: UUID): Observable<List<Referral>>

    @Query("""
      SELECT * FROM ChwReferral
      WHERE sourceUserId = :userId AND deletedAt IS NULL
      AND status NOT IN ('completed', 'cancelled', 'closed_unresolved')
      ORDER BY dueDate ASC
    """)
    fun openReferralsCreatedBy(userId: UUID): Observable<List<Referral>>

    @Query("SELECT COUNT(id) FROM ChwReferral")
    fun recordCount(): Observable<Int>

    @Query("SELECT COUNT(id) FROM ChwReferral WHERE syncStatus = :syncStatus")
    fun countWithStatus(syncStatus: SyncStatus): Observable<Int>

    @Query("SELECT * FROM ChwReferral WHERE syncStatus = :syncStatus LIMIT :limit OFFSET :offset")
    fun recordsWithSyncStatusBatched(syncStatus: SyncStatus, limit: Int, offset: Int): List<Referral>

    @Query("SELECT id FROM ChwReferral WHERE syncStatus = :syncStatus")
    fun recordIdsWithSyncStatus(syncStatus: SyncStatus): List<UUID>

    @Query("UPDATE ChwReferral SET syncStatus = :to WHERE syncStatus = :from")
    fun updateSyncStatus(from: SyncStatus, to: SyncStatus)

    @Query("UPDATE ChwReferral SET syncStatus = :to WHERE id IN (:ids)")
    fun updateSyncStatusForIds(ids: List<UUID>, to: SyncStatus)

    @Query("DELETE FROM ChwReferral")
    fun clear()

    @Query("DELETE FROM ChwReferral WHERE syncStatus = 'DONE' AND patientId NOT IN (:patientIdsInScope)")
    fun purgeSyncedOutsideScope(patientIdsInScope: List<UUID>): Int

    @Query("DELETE FROM ChwReferral WHERE deletedAt IS NOT NULL AND syncStatus = 'DONE'")
    fun purgeDeleted(): Int
  }
}
