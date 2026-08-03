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
package io.github.joelkanyi.peek.core.locator

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreType
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.core.transport.TransportException

/** Scans a debuggable app's well-known storage directories and returns the stores it finds. */
public class StoreLocator(private val transport: DeviceTransport) {

    /** Locate the key-value stores of [pkg] on [device]. */
    public suspend fun locate(device: Device, pkg: AppPackage): LocateResult {
        return try {
            val handles = buildList {
                addAll(
                    scan(device, pkg, SHARED_PREFS_DIR) { name ->
                        if (name.endsWith(".xml")) StoreType.SHARED_PREFERENCES else null
                    },
                )
                addAll(
                    scan(device, pkg, DATASTORE_DIR) { name ->
                        when {
                            name.endsWith(".preferences_pb") -> StoreType.PREFERENCES_DATASTORE
                            name.endsWith(".pb") -> StoreType.PROTO_DATASTORE
                            else -> null
                        }
                    },
                )
            }
            LocateResult.Located(handles)
        } catch (e: TransportException.NotDebuggable) {
            LocateResult.NotDebuggable(e.message ?: pkg.packageName)
        } catch (e: TransportException.PackageNotFound) {
            LocateResult.PackageNotFound(e.message ?: pkg.packageName)
        }
        // TransportException.DeviceLost propagates to the session, which pauses.
    }

    /** Build a handle for a user-supplied path, classifying by file extension. */
    public fun handleFor(pkg: AppPackage, path: String): StoreHandle {
        val name = path.substringAfterLast('/')
        val type = when {
            name.endsWith(".xml") -> StoreType.SHARED_PREFERENCES
            name.endsWith(".preferences_pb") -> StoreType.PREFERENCES_DATASTORE
            else -> StoreType.PROTO_DATASTORE
        }
        return StoreHandle(pkg, path, type, name, stat = null)
    }

    private suspend fun scan(
        device: Device,
        pkg: AppPackage,
        dir: String,
        classify: (name: String) -> StoreType?,
    ): List<StoreHandle> =
        transport.listFiles(device, pkg, dir).mapNotNull { name ->
            val type = classify(name) ?: return@mapNotNull null
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
