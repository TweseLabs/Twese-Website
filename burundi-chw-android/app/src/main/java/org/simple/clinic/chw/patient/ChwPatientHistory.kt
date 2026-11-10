package org.simple.clinic.chw.patient

import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.chw.followup.ChwContact
import org.simple.clinic.chw.screening.ScreeningEpisode
import org.simple.clinic.patient.SyncStatus
import java.time.Instant
import java.util.UUID

/**
 * One line of the combined history with explicit provenance, so a CHW screening reading is never
 * displayed as if it were a clinical BP record (and vice versa).
 */
sealed interface HistoryItem {
  val at: Instant
  val id: UUID

  data class CommunityScreening(val episode: ScreeningEpisode) : HistoryItem {
    override val at: Instant get() = episode.occurredAt
    override val id: UUID get() = episode.id
    val isPendingUpload: Boolean get() = episode.syncStatus == SyncStatus.PENDING
  }

  data class ClinicBloodPressure(val measurement: BloodPressureMeasurement) : HistoryItem {
    override val at: Instant get() = measurement.recordedAt
    override val id: UUID get() = measurement.uuid
  }

  data class ChwContactAttempt(val contact: ChwContact) : HistoryItem {
    override val at: Instant get() = contact.occurredAt
    override val id: UUID get() = contact.id
    val isPendingUpload: Boolean get() = contact.syncStatus == SyncStatus.PENDING
  }
}

object ChwPatientHistory {

  fun merge(
      screenings: List<ScreeningEpisode>,
      clinicBloodPressures: List<BloodPressureMeasurement>,
      contacts: List<ChwContact>
  ): List<HistoryItem> {
    return (screenings.filter { it.timestamps.deletedAt == null }.map(HistoryItem::CommunityScreening) +
        clinicBloodPressures.filter { it.deletedAt == null }.map(HistoryItem::ClinicBloodPressure) +
        contacts.filter { it.timestamps.deletedAt == null }.map(HistoryItem::ChwContactAttempt))
        .sortedByDescending { it.at }
  }
}
