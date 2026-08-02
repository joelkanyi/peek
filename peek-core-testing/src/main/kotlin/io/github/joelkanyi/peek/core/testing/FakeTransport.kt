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

    override suspend fun listDevices(): List<Device> = devices

    override suspend fun listDebuggableProcesses(device: Device): List<AppPackage> =
        processes[device.serial].orEmpty()

    override suspend fun exec(device: Device, command: String): ExecResult =
        ExecResult(exitCode = 0, stdout = ByteString.EMPTY, stderr = "")

    override suspend fun listFiles(device: Device, pkg: AppPackage, dir: String): List<String> {
        listFailure?.let { throw it }
        val prefix = "$dir/"
        return files.keys
            .filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
            .map { it.removePrefix(prefix) }
    }

    override suspend fun readFile(device: Device, pkg: AppPackage, path: String): ByteString {
        readSequences[path]?.let { sequence ->
            val call = readCounts.getOrElse(path) { 0 }
            readCounts[path] = call + 1
            return sequence[minOf(call, sequence.lastIndex)]
        }
        return files[path] ?: throw NoSuchElementException("no fake file at $path")
    }

    override suspend fun stat(device: Device, pkg: AppPackage, path: String): FileStat? =
        files[path]?.let { FileStat(mtimeEpochMs = 0L, sizeBytes = it.size.toLong()) }

    public companion object {
        /** Convenience for building a UTF-8 text file entry. */
        public fun textFile(contents: String): ByteString = contents.encodeUtf8()
    }
}
