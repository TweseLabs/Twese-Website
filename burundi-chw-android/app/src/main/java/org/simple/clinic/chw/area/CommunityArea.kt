package org.simple.clinic.chw.area

import android.os.Parcelable
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import io.reactivex.Observable
import kotlinx.parcelize.Parcelize
import org.simple.clinic.storage.Timestamps
import java.util.UUID

/**
 * A CHW catchment area. Burundi naming: province → commune → zone → colline (rural hill) /
 * quartier (urban). Server-managed and pull-only on the device.
 */
@Parcelize
@Entity(tableName = "CommunityArea")
data class CommunityArea(
    @PrimaryKey
    val id: UUID,
    val name: String,
    val province: String?,
    val commune: String?,
    val zone: String?,
    val colline: String?,
    val parentAreaId: UUID?,
    val active: Boolean,
    @Embedded
    val timestamps: Timestamps
) : Parcelable {

  val displayName: String
    get() = listOfNotNull(colline ?: name, commune).joinToString(", ")

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(records: List<CommunityArea>)

    @Query("SELECT * FROM CommunityArea WHERE id = :id")
    fun getOne(id: UUID): CommunityArea?

    @Query("SELECT * FROM CommunityArea WHERE id IN (:ids) AND deletedAt IS NULL")
    fun withIds(ids: List<UUID>): Observable<List<CommunityArea>>

    @Query("SELECT COUNT(id) FROM CommunityArea")
    fun recordCount(): Observable<Int>

    @Query("DELETE FROM CommunityArea")
    fun clear()
  }
}
