package io.github.joelkanyi.peek.core.session

import io.github.joelkanyi.peek.core.error.PeekError
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the read pipeline for one (device, app) pair: locate stores, fetch,
 * decode, and expose the result as a [StateFlow]. P1 fills in the fetch/decode
 * loop; polling and diffing arrive in P2.
 */
public class PeekSession(
    private val transport: DeviceTransport,
    private val device: Device,
    private val pkg: AppPackage,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Connecting)

    /** The current session state, observed by the UI. */
    public val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Re-read every store now. Manual in P1; driven by a policy in P2. */
    public fun refresh() {
        TODO("P1: locate, fetch, decode, publish Active state")
    }

    /** Stop the session and release its resources. */
    public fun close() {
        // P1: cancel in-flight work.
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

    public data object Loading : StoreState

    public class Loaded internal constructor(public val snapshot: StoreSnapshot) : StoreState

    public class Unparseable internal constructor(
        public val reason: String,
        public val hexPreview: String,
    ) : StoreState
}
