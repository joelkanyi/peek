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
package io.github.joelkanyi.peek.core.testing

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.core.transport.ExecResult
import io.github.joelkanyi.peek.core.transport.FileStat
import io.github.joelkanyi.peek.core.transport.TransportCapabilities
import io.github.joelkanyi.peek.core.transport.TransportException
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * A scriptable [DeviceTransport] for headless tests: no device required. Feed it
 * devices, per-device processes, and a path-to-bytes file map (keyed by path
 * relative to the app home dir, e.g. `shared_prefs/user.xml`). Set [listFailure]
 * to inject not-debuggable / unknown-package / device-loss conditions.
 */
public class FakeTransport(
    override val capabilities: TransportCapabilities = TransportCapabilities(canWrite = false, canPushEvents = false),
    private val devices: List<Device> = emptyList(),
    private val processes: Map<String, List<AppPackage>> = emptyMap(),
    private val files: Map<String, ByteString> = emptyMap(),
    private val listFailure: TransportException? = null,
    private val readSequences: Map<String, List<ByteString>> = emptyMap(),
) : DeviceTransport {

    private val readCounts = HashMap<String, Int>()
    private val fileStore = LinkedHashMap(files)

    override suspend fun listDevices(): List<Device> = devices

    override suspend fun listDebuggableProcesses(device: Device): List<AppPackage> =
        processes[device.serial].orEmpty()

    override suspend fun exec(device: Device, command: String): ExecResult =
        ExecResult(exitCode = 0, stdout = ByteString.EMPTY, stderr = "")

    override suspend fun listFiles(device: Device, pkg: AppPackage, dir: String): List<String> {
        listFailure?.let { throw it }
        val prefix = "$dir/"
        return fileStore.keys
            .filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
            .map { it.removePrefix(prefix) }
    }

    override suspend fun readFile(device: Device, pkg: AppPackage, path: String): ByteString {
        readSequences[path]?.let { sequence ->
            val call = readCounts.getOrElse(path) { 0 }
            readCounts[path] = call + 1
            return sequence[minOf(call, sequence.lastIndex)]
        }
        return fileStore[path] ?: throw NoSuchElementException("no fake file at $path")
    }

    override suspend fun stat(device: Device, pkg: AppPackage, path: String): FileStat? =
        fileStore[path]?.let { FileStat(mtimeEpochMs = 0L, sizeBytes = it.size.toLong()) }

    override suspend fun writeFile(device: Device, pkg: AppPackage, path: String, bytes: ByteString) {
        fileStore[path] = bytes
    }

    public companion object {
        /** Convenience for building a UTF-8 text file entry. */
        public fun textFile(contents: String): ByteString = contents.encodeUtf8()
    }
}
