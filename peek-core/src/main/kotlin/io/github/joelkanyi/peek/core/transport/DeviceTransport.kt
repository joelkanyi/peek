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
package io.github.joelkanyi.peek.core.transport

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import okio.ByteString

/** The single seam through which peek-core reaches a device. */
public interface DeviceTransport {

    /** What this channel can do. UI affordances are gated on these flags, not on the concrete type. */
    public val capabilities: TransportCapabilities

    /** Connected devices and emulators. */
    public suspend fun listDevices(): List<Device>

    /** Debuggable app processes currently running on [device]. */
    public suspend fun listDebuggableProcesses(device: Device): List<AppPackage>

    /** Run a shell [command] on [device] and collect its result. */
    public suspend fun exec(device: Device, command: String): ExecResult

    /** File names directly inside [dir] (relative to the app's private home dir), or an empty list if it does not exist. */
    public suspend fun listFiles(device: Device, pkg: AppPackage, dir: String): List<String>

    /** Read a file (path relative to the app's private home dir). */
    public suspend fun readFile(device: Device, pkg: AppPackage, path: String): ByteString

    /** File metadata, or `null` if the file does not exist. */
    public suspend fun stat(device: Device, pkg: AppPackage, path: String): FileStat?

    /** Write [bytes] to [path] atomically (tmp + rename). Requires [TransportCapabilities.canWrite]. */
    public suspend fun writeFile(device: Device, pkg: AppPackage, path: String, bytes: ByteString)
}

/** Declares what a [DeviceTransport] supports. */
public class TransportCapabilities(
    public val canWrite: Boolean,
    public val canPushEvents: Boolean,
)

/** Result of a shell command: exit code plus captured output. */
public class ExecResult(
    public val exitCode: Int,
    public val stdout: ByteString,
    public val stderr: String,
)

/** File metadata used for freshness polling and stale-write guards. */
public class FileStat(
    public val mtimeEpochMs: Long,
    public val sizeBytes: Long,
)
