package org.simple.clinic.chw.patient

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.chw.ChwTestData
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.chw.screening.BpReading
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.PlanResult
import org.simple.clinic.chw.screening.ScreeningClassifier
import org.simple.clinic.chw.screening.ScreeningInput
import org.simple.clinic.chw.screening.ScreeningPlanner
import org.simple.clinic.chw.screening.ScreeningProtocol
import org.simple.clinic.chw.screening.ScreeningRequest
import org.simple.clinic.chw.screening.SpecialPopulation
import java.time.Instant
import java.util.UUID

class ChwPatientHistoryTest {

  private val patientId = UUID.randomUUID()

  private val episode = (ScreeningPlanner(ScreeningClassifier(ScreeningProtocol.DRAFT_UNAPPROVED), ChwTestData.sequentialIds()).plan(
      ScreeningRequest(patientId, UUID.randomUUID(), ChwRole.CHW, null, UUID.randomUUID(), ChwTestData.NOW, ChwTestData.TODAY,
          ScreeningLocationType.HOUSEHOLD, "c1", true, ScreeningInput(BpReading(150, 95), null, null, setOf(DangerSign.NONE), SpecialPopulation.NONE)),
      emptyList(), emptyList()
  ) as PlanResult.Planned).plan.episode

  @Test
  fun `screenings and clinic BPs are merged newest first with explicit provenance`() {
    val olderClinicBp = TestData.bloodPressureMeasurement(patientUuid = patientId, recordedAt = Instant.parse("2026-01-01T00:00:00Z"))
    val newerClinicBp = TestData.bloodPressureMeasurement(patientUuid = patientId, recordedAt = Instant.parse("2026-04-01T00:00:00Z"))

    val history = ChwPatientHistory.merge(listOf(episode), listOf(olderClinicBp, newerClinicBp), emptyList())

    assertThat(history.map { it::class }).containsExactly(
        HistoryItem.ClinicBloodPressure::class,
        HistoryItem.CommunityScreening::class,
        HistoryItem.ClinicBloodPressure::class
    ).inOrder()
    assertThat((history[1] as HistoryItem.CommunityScreening).isPendingUpload).isTrue()
  }

  @Test
  fun `deleted records are not shown`() {
    val deleted = TestData.bloodPressureMeasurement(patientUuid = patientId, deletedAt = Instant.parse("2026-02-01T00:00:00Z"))

    assertThat(ChwPatientHistory.merge(emptyList(), listOf(deleted), emptyList())).isEmpty()
  }
}
