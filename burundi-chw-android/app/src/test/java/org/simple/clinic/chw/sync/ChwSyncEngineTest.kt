package org.simple.clinic.chw.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.sync.DataPushResponse
import org.simple.clinic.sync.ValidationErrors
import java.io.IOException
import java.util.UUID

class ChwSyncEngineTest {

  private data class Rec(val id: UUID, val value: String, var status: SyncStatus)

  /** In-memory store + fake server that upserts by id (idempotent). */
  private class Fixture {
    val local = linkedMapOf<UUID, Rec>()
    val server = linkedMapOf<UUID, String>()
    val serverRejects = mutableSetOf<UUID>()
    var failNextPush = false
    var onPush: () -> Unit = {}
    var pushCalls = 0
    var pullPages: MutableList<ChwPullResponse<Pair<UUID, String>>> = mutableListOf()
    var token: String? = null

    fun add(value: String, status: SyncStatus = SyncStatus.PENDING): Rec =
        Rec(UUID.randomUUID(), value, status).also { local[it.id] = it }

    fun channel() = ChwChannel<Rec, Pair<UUID, String>>(
        name = "Test",
        firstPendingBatch = { limit -> local.values.filter { it.status == SyncStatus.PENDING }.take(limit) },
        idOf = { it.id },
        toPayload = { it.id to it.value },
        pushCall = { payloads ->
          pushCalls++
          if (failNextPush) {
            failNextPush = false
            throw IOException("network down")
          }
          payloads.filterNot { it.first in serverRejects }.forEach { server[it.first] = it.second }
          onPush()
          DataPushResponse(payloads.filter { it.first in serverRejects }.map { ValidationErrors(it.first, listOf("bad")) })
        },
        markDone = { ids -> ids.forEach { local[it]!!.status = SyncStatus.DONE } },
        markInvalid = { ids -> ids.forEach { local[it]!!.status = SyncStatus.INVALID } },
        pendingIds = { local.values.filter { it.status == SyncStatus.PENDING }.map { it.id } },
        pullCall = { pullPages.removeAt(0) },
        payloadId = { it.first },
        saveFromServer = { payloads -> payloads.forEach { local[it.first] = Rec(it.first, it.second, SyncStatus.DONE) } },
        readToken = { token },
        writeToken = { token = it }
    )
  }

  @Test
  fun `push uploads all pending records in batches and marks only them done`() {
    val f = Fixture()
    repeat(5) { f.add("r$it") }
    val alreadyDone = f.add("old", SyncStatus.DONE)

    val report = ChwSyncEngine.push(f.channel(), batchSize = 2)

    assertThat(report.pushed).isEqualTo(5)
    assertThat(f.pushCalls).isEqualTo(3)
    assertThat(f.server.keys).doesNotContain(alreadyDone.id)
    assertThat(f.local.values.map { it.status }.toSet()).containsExactly(SyncStatus.DONE)
  }

  @Test
  fun `a record created while a push is in flight is not lost`() {
    val f = Fixture()
    f.add("first")
    var created: Rec? = null
    f.onPush = { if (created == null) created = f.add("created-during-push") }

    ChwSyncEngine.push(f.channel(), batchSize = 10)

    assertThat(f.server.keys).contains(created!!.id)
    assertThat(created!!.status).isEqualTo(SyncStatus.DONE)
  }

  @Test
  fun `network failure leaves records pending and a retry is idempotent`() {
    val f = Fixture()
    val a = f.add("a")
    val b = f.add("b")
    f.failNextPush = true

    val failed = runCatching { ChwSyncEngine.push(f.channel(), batchSize = 10) }
    assertThat(failed.isFailure).isTrue()
    assertThat(a.status).isEqualTo(SyncStatus.PENDING)
    assertThat(b.status).isEqualTo(SyncStatus.PENDING)

    ChwSyncEngine.push(f.channel(), batchSize = 10)
    ChwSyncEngine.push(f.channel(), batchSize = 10)

    assertThat(f.server).containsExactly(a.id, "a", b.id, "b")
    assertThat(a.status).isEqualTo(SyncStatus.DONE)
  }

  @Test
  fun `server-rejected records are marked invalid and not retried forever`() {
    val f = Fixture()
    val good = f.add("good")
    val bad = f.add("bad")
    f.serverRejects += bad.id

    val report = ChwSyncEngine.push(f.channel(), batchSize = 10)

    assertThat(report).isEqualTo(ChwSyncEngine.PushReport(pushed = 1, rejected = 1))
    assertThat(good.status).isEqualTo(SyncStatus.DONE)
    assertThat(bad.status).isEqualTo(SyncStatus.INVALID)
    assertThat(f.pushCalls).isEqualTo(1)
  }

  @Test
  fun `pull never overwrites a local record that is pending upload`() {
    val f = Fixture()
    val mine = f.add("local edit")
    val serverOnly = UUID.randomUUID()
    f.pullPages = mutableListOf(ChwPullResponse(listOf(mine.id to "stale server copy", serverOnly to "from server"), "t1"))

    ChwSyncEngine.pull(f.channel(), batchSize = 10)

    assertThat(f.local[mine.id]!!.value).isEqualTo("local edit")
    assertThat(f.local[mine.id]!!.status).isEqualTo(SyncStatus.PENDING)
    assertThat(f.local[serverOnly]!!.value).isEqualTo("from server")
    assertThat(f.token).isEqualTo("t1")
  }

  @Test
  fun `pull pages until a short page is returned`() {
    val f = Fixture()
    f.pullPages = mutableListOf(
        ChwPullResponse(listOf(UUID.randomUUID() to "1", UUID.randomUUID() to "2"), "t1"),
        ChwPullResponse(listOf(UUID.randomUUID() to "3"), "t2")
    )

    val received = ChwSyncEngine.pull(f.channel(), batchSize = 2)

    assertThat(received).isEqualTo(3)
    assertThat(f.token).isEqualTo("t2")
  }
}
