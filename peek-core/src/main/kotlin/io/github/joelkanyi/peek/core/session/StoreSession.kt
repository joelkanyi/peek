package io.github.joelkanyi.peek.core.session

import io.github.joelkanyi.peek.core.model.Capture
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import kotlinx.coroutines.flow.StateFlow

/**
 * A source of an app's stores that the UI can observe and edit, regardless of how
 * it is reached. [PeekSession] reaches stores over adb (files); an agent-backed
 * session reaches them live over the socket. Both expose the same [SessionState].
 */
public interface StoreSession {

    public val state: StateFlow<SessionState>

    public fun refresh()

    /** Poll on an interval (adb). No-op for push-based (agent) sessions. */
    public fun startPolling(intervalMs: Long = 3_000L)

    public fun stopPolling()

    public fun close()

    public suspend fun putValue(handle: StoreHandle, key: String, value: KvValue): WriteOutcome

    public suspend fun removeKey(handle: StoreHandle, key: String): WriteOutcome

    public suspend fun capture(name: String): Capture

    /** Add a store at a non-standard path (adb). No-op for agent sessions. */
    public fun addCustomPath(path: String)
}
