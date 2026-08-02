package io.github.joelkanyi.peek.plugin.transport

import com.intellij.openapi.extensions.ExtensionPointName
import io.github.joelkanyi.peek.core.transport.DeviceTransport

/**
 * Contributes a [DeviceTransport] to Peek. Registered through the
 * `io.github.joelkanyi.peek.transportProvider` extension point, so the way Peek
 * reaches devices is pluggable: the ddmlib provider registers in P1, the adb-CLI
 * fallback in P2, the socket agent later. When no provider is registered (for
 * example plain IntelliJ without the Android plugin), the tool window shows an
 * explanatory empty state.
 */
public interface TransportProvider {

    /** Human-readable name shown in the UI, e.g. "ddmlib". */
    public val displayName: String

    /** Create the transport, or `null` if it is unavailable in this IDE/environment. */
    public fun createTransport(): DeviceTransport?

    public companion object {
        public val EP_NAME: ExtensionPointName<TransportProvider> =
            ExtensionPointName.create("io.github.joelkanyi.peek.transportProvider")
    }
}
