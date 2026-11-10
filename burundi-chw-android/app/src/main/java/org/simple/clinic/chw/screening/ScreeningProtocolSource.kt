package org.simple.clinic.chw.screening

import android.app.Application
import com.squareup.moshi.Moshi
import org.simple.clinic.platform.crash.CrashReporter
import javax.inject.Inject

/**
 * Loads the versioned screening protocol from `assets/burundi/screening_protocol.json`.
 *
 * Any failure to load/parse falls back to [ScreeningProtocol.DRAFT_UNAPPROVED], which is flagged
 * as unapproved, so the UI keeps showing the "draft protocol – not clinically approved" banner.
 * Server-delivered protocol configuration is a server-blocked follow-up.
 */
class ScreeningProtocolSource @Inject constructor(
    private val application: Application,
    private val moshi: Moshi
) {

  @Volatile
  private var cached: ScreeningProtocol? = null

  fun current(): ScreeningProtocol {
    cached?.let { return it }
    val loaded = try {
      application.assets.open(ASSET_PATH).bufferedReader().use { reader ->
        parse(moshi, reader.readText())
      }
    } catch (e: Exception) {
      CrashReporter.report(e)
      null
    }
    return (loaded ?: ScreeningProtocol.DRAFT_UNAPPROVED).also { cached = it }
  }

  companion object {
    const val ASSET_PATH = "burundi/screening_protocol.json"

    fun parse(moshi: Moshi, json: String): ScreeningProtocol? =
        moshi.adapter(ScreeningProtocol::class.java).lenient().fromJson(json)
  }
}
