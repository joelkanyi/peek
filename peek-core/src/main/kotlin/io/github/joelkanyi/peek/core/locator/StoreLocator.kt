package io.github.joelkanyi.peek.core.locator

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.transport.DeviceTransport

/**
 * Scans a debuggable app's well-known storage directories and returns the
 * stores it finds. Behavior arrives in P1; this fixes the P0 API surface.
 */
public class StoreLocator(private val transport: DeviceTransport) {

    /** Locate the key-value stores of [pkg] on [device]. */
    public suspend fun locate(device: Device, pkg: AppPackage): LocateResult {
        TODO("P1: scan shared_prefs/ and files/datastore/ via transport")
    }
}

/** Outcome of a [StoreLocator.locate] call. */
public sealed interface LocateResult {

    public class Located internal constructor(public val handles: List<StoreHandle>) : LocateResult

    public class NotDebuggable internal constructor(public val raw: String) : LocateResult

    public class PackageNotFound internal constructor(public val raw: String) : LocateResult
}
