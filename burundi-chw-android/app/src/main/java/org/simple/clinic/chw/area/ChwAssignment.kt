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
import org.simple.clinic.chw.AssignmentStatus
import org.simple.clinic.storage.Timestamps
import java.time.LocalDate
import java.util.UUID

/**
 * Assignment of a CHW user to a community area with a supervising clinic.
 * Server-authoritative, pull-only on device.
 */
@Parcelize
@Entity(
    tableName = "ChwAssignment",
    indices = [Index("chwUserId")]
)
data class ChwAssignment(
    @PrimaryKey
    val id: UUID,
    val chwUserId: UUID,
    val communityAreaId: UUID,
    val supervisingFacilityId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: AssignmentStatus,
    @Embedded
    val timestamps: Timestamps
) : Parcelable {

  fun isActiveOn(date: LocalDate): Boolean =
      status == AssignmentStatus.ACTIVE &&
          timestamps.deletedAt == null &&
          !date.isBefore(startDate) &&
          (endDate == null || !date.isAfter(endDate))

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(records: List<ChwAssignment>)

    @Query("SELECT * FROM ChwAssignment WHERE chwUserId = :chwUserId AND deletedAt IS NULL ORDER BY startDate DESC")
    fun forUser(chwUserId: UUID): Observable<List<ChwAssignment>>

    @Query("SELECT * FROM ChwAssignment WHERE chwUserId = :chwUserId AND deletedAt IS NULL ORDER BY startDate DESC")
    fun forUserImmediate(chwUserId: UUID): List<ChwAssignment>

    @Query("SELECT COUNT(id) FROM ChwAssignment")
    fun recordCount(): Observable<Int>

    @Query("DELETE FROM ChwAssignment")
    fun clear()
  }
}
