package org.simple.clinic.chw.sync

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Test
import org.simple.clinic.chw.ChwTestData
import org.simple.clinic.chw.ScreeningLocationType
import org.simple.clinic.chw.screening.BpReading
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.PlanResult
import org.simple.clinic.chw.screening.ScreeningClassifier
import org.simple.clinic.chw.screening.ScreeningInput
import org.simple.clinic.chw.screening.ScreeningPlanner
import org.simple.clinic.chw.screening.ScreeningProtocol
import org.simple.clinic.chw.screening.ScreeningRequest
import org.simple.clinic.chw.screening.SpecialPopulation
import org.simple.clinic.chw.access.ChwRole
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.util.moshi.InstantMoshiAdapter
import org.simple.clinic.util.moshi.LocalDateMoshiAdapter
import org.simple.clinic.util.moshi.UuidMoshiAdapter
import java.util.UUID

class ChwPayloadTest {

  private val moshi = Moshi.Builder()
      .add(InstantMoshiAdapter())
      .add(LocalDateMoshiAdapter())
      .add(UuidMoshiAdapter())
      .build()

  private val plan = (ScreeningPlanner(ScreeningClassifier(ScreeningProtocol.DRAFT_UNAPPROVED), ChwTestData.sequentialIds()).plan(
      ScreeningRequest(
          patientId = UUID.randomUUID(),
          chwUserId = UUID.randomUUID(),
          role = ChwRole.CHW,
          communityAreaId = UUID.randomUUID(),
          supervisingFacilityId = UUID.randomUUID(),
          occurredAt = ChwTestData.NOW,
          today = ChwTestData.TODAY,
          locationType = ScreeningLocationType.COMMUNITY_SCREENING_POINT,
          consentVersion = "consent-draft-1",
          consentGiven = true,
          input = ScreeningInput(BpReading(185, 100), BpReading(182, 98), 3, setOf(DangerSign.SEVERE_HEADACHE, DangerSign.VISION_CHANGES), SpecialPopulation.NONE)
      ), emptyList(), emptyList()
  ) as PlanResult.Planned).plan

  @Test
  fun `screening episode survives a JSON round trip`() {
    val adapter = moshi.adapter(ScreeningEpisodePayload::class.java)
    val json = adapter.toJson(ScreeningEpisodePayload.from(plan.episode))

    val back = adapter.fromJson(json)!!.toDatabaseModel(SyncStatus.PENDING)

    assertThat(back).isEqualTo(plan.episode)
    assertThat(json).contains("\"workflow_outcome\":\"urgent_clinical_assessment\"")
    assertThat(json).contains("\"danger_signs\":[\"severe_headache\",\"vision_changes\"]")
  }

  @Test
  fun `referral survives a JSON round trip inside the versioned push envelope`() {
    val type = Types.newParameterizedType(ChwPushRequest::class.java, ReferralPayload::class.java)
    val adapter = moshi.adapter<ChwPushRequest<ReferralPayload>>(type)
    val json = adapter.toJson(ChwPushRequest(records = listOf(ReferralPayload.from(plan.referral!!))))

    val back = adapter.fromJson(json)!!

    assertThat(back.apiVersion).isEqualTo(CHW_API_VERSION)
    assertThat(back.records.single().toDatabaseModel(SyncStatus.PENDING)).isEqualTo(plan.referral)
    assertThat(json).contains("\"status\":\"submitted_pending_sync\"")
  }

  @Test
  fun `unknown codes from a newer server do not crash and map to safe values`() {
    val payload = ReferralPayload.from(plan.referral!!).copy(status = "new_server_status", priority = "new_priority")

    val model = payload.toDatabaseModel(SyncStatus.DONE)

    assertThat(model.status.code).isEqualTo("unknown")
    assertThat(model.priority.code).isEqualTo("unknown")
  }
}
