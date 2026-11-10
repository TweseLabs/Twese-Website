package org.simple.clinic.chw.screening

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import org.junit.Test
import java.io.File

class ScreeningProtocolAssetTest {

  private val assetFile = File("src/main/assets/${ScreeningProtocolSource.ASSET_PATH}")

  @Test
  fun `the bundled protocol asset parses and matches the in-code draft fallback`() {
    val parsed = ScreeningProtocolSource.parse(Moshi.Builder().build(), assetFile.readText())

    assertThat(parsed).isEqualTo(ScreeningProtocol.DRAFT_UNAPPROVED)
  }

  @Test
  fun `the bundled protocol is clearly marked as not approved`() {
    val parsed = ScreeningProtocolSource.parse(Moshi.Builder().build(), assetFile.readText())!!

    // Flip this assertion only as part of the formal clinical sign-off change.
    assertThat(parsed.approved).isFalse()
    assertThat(parsed.approvedBy).isNull()
    assertThat(parsed.version).contains("unapproved")
  }
}
