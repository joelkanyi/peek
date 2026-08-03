/*
 * Copyright 2026 Joel Kanyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.joelkanyi.peek.core.session

import io.github.joelkanyi.peek.core.codec.StoreCodecs
import io.github.joelkanyi.peek.core.error.PeekError
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Capture
import io.github.joelkanyi.peek.core.model.CapturedStore
import io.github.joelkanyi.peek.core.model.KvEntry
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreDiff
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.model.StoreType
import io.github.joelkanyi.peek.core.model.sameAs
import io.github.joelkanyi.peek.wire.Message
import io.github.joelkanyi.peek.wire.PROTOCOL_VERSION
import io.github.joelkanyi.peek.wire.StoreInfo
import io.github.joelkanyi.peek.wire.StoreKind
import io.github.joelkanyi.peek.wire.WireCodec
import io.github.joelkanyi.peek.wire.WireValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.buffer
import okio.sink
import okio.source
import java.net.Socket

/** A [StoreSession] backed by the on-device agent over a socket. */
public class AgentSession(
    private val socket: Socket,
    private val pkg: AppPackage,
    private val scope: CoroutineScope,
) : StoreSession {

    private val _state = MutableStateFlow<SessionState>(SessionState.Connecting)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val source = socket.getInputStream().source().buffer()
    private val sink = socket.getOutputStream().sink().buffer()
    private val sendMutex = Mutex()
    private val editMutex = Mutex()

    @Volatile
    private var pendingEdit: CompletableDeferred<WriteOutcome>? = null

    private val infos = LinkedHashMap<String, StoreInfo>()
    private val entries = LinkedHashMap<String, List<KvEntry>>()
    private val previous = HashMap<String, List<KvEntry>>()
    private val diffs = HashMap<String, StoreDiff>()
    private var readJob: Job? = null

    /** Start the read loop and greet the agent. */
    public fun connect() {
        readJob = scope.launch(Dispatchers.IO) { readLoop() }
        scope.launch(Dispatchers.IO) { send(Message.Hello(PROTOCOL_VERSION)) }
    }

    private suspend fun readLoop() {
        try {
            while (true) {
                val message = WireCodec.readFrame(source) ?: break
                onMessage(message)
            }
            _state.value = SessionState.Paused(PeekError.DeviceLost(pkg.packageName))
        } catch (e: Exception) {
            _state.value = SessionState.Failed(PeekError.TransportFailure(e.message ?: "agent connection lost"))
        }
    }

    private suspend fun onMessage(message: Message) {
        when (message) {
            is Message.Welcome -> send(Message.ListStores)
            is Message.StoreList -> {
                infos.clear()
                message.stores.forEach { infos[it.id] = it }
                message.stores.forEach { send(Message.ReadStore(it.id)) }
                publish()
            }
            is Message.StoreData -> {
                val list = message.entries.map { KvEntry(it.key, it.value.toKv()) }
                diffs[message.storeId] = entriesDiff(previous[message.storeId], list)
                previous[message.storeId] = list
                entries[message.storeId] = list
                publish()
            }
            is Message.Changed -> send(Message.ReadStore(message.storeId))
            Message.Ok -> pendingEdit?.complete(WriteOutcome.Applied)
            is Message.Err -> pendingEdit?.complete(WriteOutcome.Refused(message.message))
            else -> Unit
        }
    }

    private fun publish() {
        val stores = infos.values.map { info ->
            val handle = StoreHandle(pkg, info.id, info.kind.toStoreType(), info.displayName, stat = null)
            StoreState.Loaded(handle, StoreSnapshot(handle, entries[info.id].orEmpty(), 0L), diffs[info.id] ?: StoreDiff.NONE)
        }
        _state.value = SessionState.Active(stores)
    }

    override fun refresh() {
        scope.launch(Dispatchers.IO) {
            if (infos.isEmpty()) send(Message.ListStores) else infos.keys.forEach { send(Message.ReadStore(it)) }
        }
    }

    override fun startPolling(intervalMs: Long) {}

    override fun stopPolling() {}

    override fun addCustomPath(path: String) {}

    override fun close() {
        readJob?.cancel()
        runCatching { socket.close() }
    }

    override suspend fun putValue(handle: StoreHandle, key: String, value: KvValue): WriteOutcome = editMutex.withLock {
        val wire = value.toWire() ?: return@withLock WriteOutcome.Refused("the agent cannot store that value type")
        awaitEdit(Message.PutValue(handle.path, key, wire))
    }

    override suspend fun removeKey(handle: StoreHandle, key: String): WriteOutcome = editMutex.withLock {
        awaitEdit(Message.RemoveKey(handle.path, key))
    }

    private suspend fun awaitEdit(message: Message): WriteOutcome {
        val deferred = CompletableDeferred<WriteOutcome>()
        pendingEdit = deferred
        send(message)
        return withTimeoutOrNull(EDIT_TIMEOUT_MS) { deferred.await() } ?: WriteOutcome.Refused("the agent did not respond")
    }

    override suspend fun capture(name: String): Capture {
        val stores = infos.values.map { info ->
            val handle = StoreHandle(pkg, info.id, info.kind.toStoreType(), info.displayName, stat = null)
            val bytes = StoreCodecs.codecFor(handle.type).encode(StoreSnapshot(handle, entries[info.id].orEmpty(), 0L))
            CapturedStore(info.id, handle.type, info.displayName, bytes)
        }
        return Capture(name, System.currentTimeMillis(), stores)
    }

    private suspend fun send(message: Message) = sendMutex.withLock {
        withContext(Dispatchers.IO) { WireCodec.writeFrame(sink, message) }
    }

    private fun StoreKind.toStoreType(): StoreType = when (this) {
        StoreKind.SHARED_PREFERENCES -> StoreType.SHARED_PREFERENCES
        StoreKind.PREFERENCES_DATASTORE -> StoreType.PREFERENCES_DATASTORE
    }

    private fun WireValue.toKv(): KvValue = when (this) {
        is WireValue.BoolValue -> KvValue.of(value)
        is WireValue.IntValue -> KvValue.of(value)
        is WireValue.LongValue -> KvValue.of(value)
        is WireValue.FloatValue -> KvValue.of(value)
        is WireValue.DoubleValue -> KvValue.of(value)
        is WireValue.StringValue -> KvValue.of(value)
        is WireValue.StringSetValue -> KvValue.of(values.toSet())
        is WireValue.BytesValue -> KvValue.of(value)
    }

    private fun KvValue.toWire(): WireValue? = when (this) {
        is KvValue.BoolValue -> WireValue.BoolValue(value)
        is KvValue.IntValue -> WireValue.IntValue(value)
        is KvValue.LongValue -> WireValue.LongValue(value)
        is KvValue.FloatValue -> WireValue.FloatValue(value)
        is KvValue.DoubleValue -> WireValue.DoubleValue(value)
        is KvValue.StringValue -> WireValue.StringValue(value)
        is KvValue.StringSetValue -> WireValue.StringSetValue(values.toList())
        is KvValue.BytesValue -> WireValue.BytesValue(value)
        is KvValue.ProtoNode -> null
    }

    private fun entriesDiff(previous: List<KvEntry>?, next: List<KvEntry>): StoreDiff {
        if (previous == null) return StoreDiff.NONE
        val prevMap = previous.associate { it.key to it.value }
        val nextMap = next.associate { it.key to it.value }
        val changed = nextMap.keys.intersect(prevMap.keys)
            .filterTo(LinkedHashSet()) { !prevMap.getValue(it).sameAs(nextMap.getValue(it)) }
        return StoreDiff(nextMap.keys - prevMap.keys, changed, prevMap.keys - nextMap.keys)
    }

    private companion object {
        const val EDIT_TIMEOUT_MS = 5_000L
    }
}
