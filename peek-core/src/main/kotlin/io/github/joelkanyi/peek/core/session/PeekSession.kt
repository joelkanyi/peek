package io.github.joelkanyi.peek.core.session

import io.github.joelkanyi.peek.core.codec.DecodeResult
import io.github.joelkanyi.peek.core.codec.PreferencesPbCodec
import io.github.joelkanyi.peek.core.codec.ProtoDataStoreCodec
import io.github.joelkanyi.peek.core.codec.SharedPreferencesXmlCodec
import io.github.joelkanyi.peek.core.codec.StoreCodec
import io.github.joelkanyi.peek.core.error.PeekError
import io.github.joelkanyi.peek.core.locator.LocateResult
import io.github.joelkanyi.peek.core.locator.StoreLocator
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.StoreDiff
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.model.StoreType
import io.github.joelkanyi.peek.core.model.sameAs
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.core.transport.TransportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString

/**
 * Owns the read pipeline for one (device, app) pair: locate stores, fetch, decode,
 * and expose the result as a [StateFlow]. Refresh is manual in P1; a polling policy
 * drives it in P2.
 */
public class PeekSession internal constructor(
    private val transport: DeviceTransport,
    private val device: Device,
    private val pkg: AppPackage,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val retryDelayMs: Long,
) {
    public constructor(
        transport: DeviceTransport,
        device: Device,
        pkg: AppPackage,
        scope: CoroutineScope,
    ) : this(transport, device, pkg, scope, System::currentTimeMillis, DEFAULT_RETRY_DELAY_MS)

    private val locator = StoreLocator(transport)
    private val codecs: Map<StoreType, StoreCodec> = mapOf(
        StoreType.SHARED_PREFERENCES to SharedPreferencesXmlCodec(),
        StoreType.PREFERENCES_DATASTORE to PreferencesPbCodec(),
        StoreType.PROTO_DATASTORE to ProtoDataStoreCodec(),
    )

    private var previousSnapshots: Map<String, StoreSnapshot> = emptyMap()
    private val refreshMutex = Mutex()
    private var policy: RefreshPolicy? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.Connecting)

    /** The current session state, observed by the UI. */
    public val state: StateFlow<SessionState> = _state.asStateFlow()

    private var job: Job? = null

    /** Re-read every store now. Manual in P1; driven by a policy in P2. */
    public fun refresh() {
        job?.cancel()
        job = scope.launch { doRefresh() }
    }

    /** Begin polling every [intervalMs]. Idempotent; call [stopPolling] to pause. */
    public fun startPolling(intervalMs: Long = DEFAULT_POLL_INTERVAL_MS) {
        policy?.stop()
        policy = RefreshPolicy(scope, intervalMs) { doRefresh() }.also { it.start() }
    }

    /** Stop polling. The last loaded state remains visible. */
    public fun stopPolling() {
        policy?.stop()
        policy = null
    }

    /** Stop the session and release its resources. */
    public fun close() {
        stopPolling()
        job?.cancel()
    }

    // Serialized so a manual refresh and a poll tick never run concurrently
    // (they share previousSnapshots and the state flow).
    private suspend fun doRefresh() = refreshMutex.withLock {
        try {
            when (val located = locator.locate(device, pkg)) {
                is LocateResult.NotDebuggable ->
                    _state.value = SessionState.Failed(PeekError.NotDebuggable(pkg.packageName))
                is LocateResult.PackageNotFound ->
                    _state.value = SessionState.Failed(PeekError.PackageNotFound(pkg.packageName))
                is LocateResult.Located -> {
                    // Load every store concurrently: each is an independent adb round trip.
                    // Read the baseline before the fan-out; update it after joining (no shared writes).
                    val baseline = previousSnapshots
                    val stores = coroutineScope {
                        located.handles.map { async { loadStore(it, baseline[it.path]) } }.awaitAll()
                    }
                    previousSnapshots = baseline + stores.filterIsInstance<StoreState.Loaded>()
                        .associate { it.handle.path to it.snapshot }
                    _state.value = SessionState.Active(stores)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransportException.DeviceLost) {
            _state.value = SessionState.Paused(PeekError.DeviceLost(device.serial))
        } catch (e: Exception) {
            _state.value = SessionState.Failed(PeekError.TransportFailure(e.message ?: "transport failure"))
        }
    }

    private suspend fun loadStore(handle: StoreHandle, previous: StoreSnapshot?): StoreState {
        val codec = codecs[handle.type]
            ?: return StoreState.Unparseable(handle, PROTO_LATER, hexPreview = "")

        var lastFailure: DecodeResult.Failed? = null
        // A DataStore write is a tmp-file rename, so a torn read is transient:
        // decode failure triggers exactly one retry before we give up to a hex row.
        repeat(MAX_ATTEMPTS) { attempt ->
            val bytes = try {
                transport.readFile(device, pkg, handle.path)
            } catch (e: TransportException.DeviceLost) {
                throw e
            } catch (e: Exception) {
                return StoreState.Unparseable(handle, e.message ?: "read failed", hexPreview = "")
            }
            when (val decoded = codec.decode(handle, bytes, now())) {
                is DecodeResult.Decoded ->
                    return StoreState.Loaded(handle, decoded.snapshot, diff(previous, decoded.snapshot))
                is DecodeResult.Failed -> lastFailure = decoded
            }
            if (attempt == 0) delay(retryDelayMs)
        }
        val failure = lastFailure!!
        return StoreState.Unparseable(handle, failure.reason, hexPreview(failure.bytes))
    }

    private fun diff(previous: StoreSnapshot?, next: StoreSnapshot): StoreDiff {
        if (previous == null) return StoreDiff.NONE
        val prevMap = previous.entries.associate { it.key to it.value }
        val nextMap = next.entries.associate { it.key to it.value }
        val changed = nextMap.keys.intersect(prevMap.keys)
            .filterTo(LinkedHashSet()) { !prevMap.getValue(it).sameAs(nextMap.getValue(it)) }
        return StoreDiff(
            added = nextMap.keys - prevMap.keys,
            changed = changed,
            removed = prevMap.keys - nextMap.keys,
        )
    }

    private fun hexPreview(bytes: ByteString): String {
        val shown = minOf(bytes.size, HEX_PREVIEW_BYTES)
        return bytes.substring(0, shown).hex() + if (bytes.size > shown) "…" else ""
    }

    private companion object {
        const val DEFAULT_RETRY_DELAY_MS = 200L
        const val DEFAULT_POLL_INTERVAL_MS = 3_000L
        const val MAX_ATTEMPTS = 2
        const val HEX_PREVIEW_BYTES = 32
        const val PROTO_LATER = "Proto DataStore support arrives in a later version"
    }
}

/** The observable state of a [PeekSession]. */
public sealed interface SessionState {

    public data object Connecting : SessionState

    public class Active internal constructor(public val stores: List<StoreState>) : SessionState

    /** The device was lost; the session pauses and resumes on reconnect. */
    public class Paused internal constructor(public val error: PeekError) : SessionState

    public class Failed internal constructor(public val error: PeekError) : SessionState
}

/** The state of a single store within an [SessionState.Active] session. */
public sealed interface StoreState {

    /** The store this state describes. */
    public val handle: StoreHandle

    public class Loading internal constructor(override val handle: StoreHandle) : StoreState

    public class Loaded internal constructor(
        override val handle: StoreHandle,
        public val snapshot: StoreSnapshot,
        public val diff: StoreDiff,
    ) : StoreState

    public class Unparseable internal constructor(
        override val handle: StoreHandle,
        public val reason: String,
        public val hexPreview: String,
    ) : StoreState
}
