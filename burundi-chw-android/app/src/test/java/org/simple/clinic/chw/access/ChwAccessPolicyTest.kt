package org.simple.clinic.chw.access

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.chw.access.ChwPermission.CONFIRM_HYPERTENSION_DIAGNOSIS
import org.simple.clinic.chw.access.ChwPermission.DISPOSITION_REFERRAL
import org.simple.clinic.chw.access.ChwPermission.EDIT_MEDICATION_ORDERS
import org.simple.clinic.chw.access.ChwPermission.EXPORT_PATIENT_LISTS
import org.simple.clinic.chw.access.ChwPermission.RECORD_COMMUNITY_SCREENING
import java.util.UUID

class ChwAccessPolicyTest {

  @Test
  fun `CHW cannot confirm diagnosis, edit medication, disposition referrals or export`() {
    listOf(CONFIRM_HYPERTENSION_DIAGNOSIS, EDIT_MEDICATION_ORDERS, DISPOSITION_REFERRAL, EXPORT_PATIENT_LISTS, ChwPermission.BROWSE_ALL_REGIONS)
        .forEach { assertThat(ChwAccessPolicy.isAllowed(ChwRole.CHW, it)).isFalse() }
  }

  @Test
  fun `CHW can screen, follow up and refer`() {
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.CHW, RECORD_COMMUNITY_SCREENING)).isTrue()
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.CHW, ChwPermission.RECORD_CHW_FOLLOW_UP)).isTrue()
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.CHW, ChwPermission.CREATE_REFERRAL)).isTrue()
  }

  @Test
  fun `clinician confirms diagnosis and prescribes but does not record community screening`() {
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.CLINICIAN, CONFIRM_HYPERTENSION_DIAGNOSIS)).isTrue()
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.CLINICIAN, EDIT_MEDICATION_ORDERS)).isTrue()
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.CLINICIAN, DISPOSITION_REFERRAL)).isTrue()
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.CLINICIAN, RECORD_COMMUNITY_SCREENING)).isFalse()
  }

  @Test
  fun `field coordinator and programme admin have no prescribing or diagnosis rights`() {
    listOf(ChwRole.FIELD_COORDINATOR, ChwRole.PROGRAM_ADMIN).forEach { role ->
      assertThat(ChwAccessPolicy.isAllowed(role, EDIT_MEDICATION_ORDERS)).isFalse()
      assertThat(ChwAccessPolicy.isAllowed(role, CONFIRM_HYPERTENSION_DIAGNOSIS)).isFalse()
    }
  }

  @Test
  fun `no role is granted export or all-region browsing on the device`() {
    ChwRole.entries.forEach { role ->
      ChwAccessPolicy.permissionsNeverGrantedOnDevice.forEach { permission ->
        assertThat(ChwAccessPolicy.isAllowed(role, permission)).isFalse()
      }
    }
  }

  @Test
  fun `legacy clinic users keep upstream clinical capabilities`() {
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.LEGACY_CLINIC_USER, EDIT_MEDICATION_ORDERS)).isTrue()
    assertThat(ChwAccessPolicy.isAllowed(ChwRole.LEGACY_CLINIC_USER, CONFIRM_HYPERTENSION_DIAGNOSIS)).isTrue()
  }

  @Test
  fun `unknown role codes fall back to the legacy role, never to a programme role`() {
    assertThat(ChwRole.fromCode("super_admin")).isEqualTo(ChwRole.LEGACY_CLINIC_USER)
    assertThat(ChwRole.fromCode(null)).isEqualTo(ChwRole.LEGACY_CLINIC_USER)
    assertThat(ChwRole.fromCode("chw")).isEqualTo(ChwRole.CHW)
  }

  @Test
  fun `CHW may only act on patients actively assigned to them`() {
    val me = UUID.randomUUID()
    val other = UUID.randomUUID()

    assertThat(ChwAccessPolicy.canActOnPatient(ChwRole.CHW, me, setOf(me))).isTrue()
    assertThat(ChwAccessPolicy.canActOnPatient(ChwRole.CHW, me, setOf(other))).isFalse()
    assertThat(ChwAccessPolicy.canActOnPatient(ChwRole.CHW, me, emptySet())).isFalse()
    assertThat(ChwAccessPolicy.canActOnPatient(ChwRole.PROGRAM_ADMIN, me, setOf(me))).isFalse()
  }
}
