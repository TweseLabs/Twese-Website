package org.simple.clinic.storage.migrations

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.assertIndexExists
import org.simple.clinic.assertTableDoesNotExist
import org.simple.clinic.assertTableExists
import org.simple.clinic.insert

class Migration124AndroidTest : BaseDatabaseMigrationTest(123, 124) {

  private val newTables = listOf(
      "CommunityArea",
      "ChwAssignment",
      "PatientChwAssignment",
      "ScreeningEpisode",
      "ChwReferral",
      "ChwFollowUpTask",
      "ChwContact"
  )

  @Test
  fun migration_124_creates_the_burundi_chw_tables_and_indices() {
    newTables.forEach { before.assertTableDoesNotExist(it) }

    newTables.forEach { after.assertTableExists(it) }
    after.assertIndexExists("index_ScreeningEpisode_patientId")
    after.assertIndexExists("index_ChwReferral_destinationFacilityId")
    after.assertIndexExists("index_ChwFollowUpTask_chwUserId_status_dueDate")
    after.assertIndexExists("index_PatientChwAssignment_chwUserId_status")
    after.assertIndexExists("index_ChwContact_taskId")
  }

  @Test
  fun migration_124_preserves_existing_rows_in_upstream_tables() {
    // Synthetic row in an existing upstream table.
    before.insert("CallResult", mapOf(
        "id" to "22222222-2222-2222-2222-222222222222",
        "userId" to "33333333-3333-3333-3333-333333333333",
        "patientId" to "11111111-1111-1111-1111-111111111111",
        "facilityId" to null,
        "appointmentId" to "44444444-4444-4444-4444-444444444444",
        "removeReason" to null,
        "outcome" to "agreed_to_visit",
        "syncStatus" to "PENDING",
        "createdAt" to "2026-01-01T00:00:00Z",
        "updatedAt" to "2026-01-01T00:00:00Z",
        "deletedAt" to null
    ))

    after.query("SELECT outcome, syncStatus FROM CallResult WHERE id = '22222222-2222-2222-2222-222222222222'").use {
      assertThat(it.count).isEqualTo(1)
      it.moveToFirst()
      assertThat(it.getString(0)).isEqualTo("agreed_to_visit")
      assertThat(it.getString(1)).isEqualTo("PENDING")
    }
  }
}
