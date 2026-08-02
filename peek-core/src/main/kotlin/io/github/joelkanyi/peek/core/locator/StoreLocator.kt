package io.github.joelkanyi.peek.core.locator

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreType
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.core.transport.TransportException

/**
 * Scans a debuggable app's well-known storage directories and returns the stores
 * it finds. It works purely through [DeviceTransport], so it is agnostic of how
 * the bytes are reached (adb today, an agent later).
 */
public class StoreLocator(private val transport: DeviceTransport) {

    /** Locate the key-value stores of [pkg] on [device]. */
    public suspend fun locate(device: Device, pkg: AppPackage): LocateResult {
        return try {
            val handles = buildList {
                addAll(scan(device, pkg, SHARED_PREFS_DIR) { name ->
                    if (name.endsWith(".xml")) StoreType.SHARED_PREFERENCES else null
                })
                addAll(scan(device, pkg, DATASTORE_DIR) { name ->
                    when {
                        name.endsWith(".preferences_pb") -> StoreType.PREFERENCES_DATASTORE
                        name.endsWith(".pb") -> StoreType.PROTO_DATASTORE
                        else -> null
                    }
                })
            }
            LocateResult.Located(handles)
        } catch (e: TransportException.NotDebuggable) {
            LocateResult.NotDebuggable(e.message ?: pkg.packageName)
        } catch (e: TransportException.PackageNotFound) {
            LocateResult.PackageNotFound(e.message ?: pkg.packageName)
        }
        // TransportException.DeviceLost propagates to the session, which pauses.
    }

    private suspend fun scan(
        device: Device,
        pkg: AppPackage,
        dir: String,
        classify: (name: String) -> StoreType?,
    ): List<StoreHandle> =
        transport.listFiles(device, pkg, dir).mapNotNull { name ->
            val type = classify(name) ?: return@mapNotNull null
            // No stat here: P1 does not use mtime, and one adb call per file is slow.
            // P2 reintroduces it, batched, for polling.
            StoreHandle(pkg, "$dir/$name", type, name, stat = null)
        }

    private companion object {
        const val SHARED_PREFS_DIR = "shared_prefs"
        const val DATASTORE_DIR = "files/datastore"
    }
}

/** Outcome of a [StoreLocator.locate] call. */
public sealed interface LocateResult {

    public class Located internal constructor(public val handles: List<StoreHandle>) : LocateResult

    public class NotDebuggable internal constructor(public val raw: String) : LocateResult

    public class PackageNotFound internal constructor(public val raw: String) : LocateResult
}
