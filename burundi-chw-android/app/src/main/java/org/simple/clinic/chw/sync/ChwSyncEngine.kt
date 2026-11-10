package org.simple.clinic.chw.sync

import org.simple.clinic.sync.DataPushResponse
import java.util.UUID

/**
 * One syncable CHW record type, expressed as plain functions so the push/pull algorithm can be
 * unit tested with in-memory fakes.
 */
class ChwChannel<T, P>(
    val name: String,
    val firstPendingBatch: (limit: Int) -> List<T>,
    val idOf: (T) -> UUID,
    val toPayload: (T) -> P,
    val pushCall: ((List<P>) -> DataPushResponse)?,
    val markDone: (List<UUID>) -> Unit,
    val markInvalid: (List<UUID>) -> Unit,
    val pendingIds: () -> Collection<UUID>,
    val pullCall: (token: String?) -> ChwPullResponse<P>,
    val payloadId: (P) -> UUID,
    val saveFromServer: (List<P>) -> Unit,
    val readToken: () -> String?,
    val writeToken: (String) -> Unit
)

/**
 * Push/pull algorithm for CHW records.
 *
 * Differences from upstream [org.simple.clinic.sync.SyncCoordinator], deliberately:
 * - Only the ids actually sent in a successful request are marked DONE. A record created while
 *   a push is in flight therefore stays PENDING and is sent next time (no silent loss).
 * - Records rejected by the server are marked INVALID and are never retried automatically.
 * - On any network/server exception nothing is marked; the whole batch is retried later and the
 *   server upserts by client UUID, so retries are idempotent.
 * - On pull, server records never overwrite a local record that is still PENDING upload; the
 *   server then resolves the conflict when that record is pushed (server is authoritative for
 *   referral status transitions).
 */
object ChwSyncEngine {

  const val MAX_PUSH_ROUNDS = 1_000

  fun <T, P> push(channel: ChwChannel<T, P>, batchSize: Int): PushReport {
    val call = channel.pushCall ?: return PushReport(0, 0)
    var pushed = 0
    var invalid = 0
    var rounds = 0
    var batch = channel.firstPendingBatch(batchSize)

    while (batch.isNotEmpty() && rounds < MAX_PUSH_ROUNDS) {
      rounds++
      val response = call(batch.map(channel.toPayload))
      val batchIds = batch.map(channel.idOf)
      val rejected = response.validationErrors.map { it.uuid }.filter { it in batchIds }.toSet()

      if (rejected.isNotEmpty()) channel.markInvalid(rejected.toList())
      val accepted = batchIds.filterNot { it in rejected }
      if (accepted.isNotEmpty()) channel.markDone(accepted)

      pushed += accepted.size
      invalid += rejected.size
      batch = channel.firstPendingBatch(batchSize)
    }
    return PushReport(pushed = pushed, rejected = invalid)
  }

  fun <T, P> pull(channel: ChwChannel<T, P>, batchSize: Int): Int {
    var received = 0
    var done = false
    while (!done) {
      val response = channel.pullCall(channel.readToken())
      val pending = channel.pendingIds().toSet()
      val toSave = response.records.filterNot { channel.payloadId(it) in pending }
      channel.saveFromServer(toSave)
      channel.writeToken(response.processToken)
      received += toSave.size
      done = response.records.size < batchSize
    }
    return received
  }

  data class PushReport(val pushed: Int, val rejected: Int)
}
