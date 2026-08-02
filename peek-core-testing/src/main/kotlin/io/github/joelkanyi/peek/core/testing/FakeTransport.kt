package io.github.joelkanyi.peek.core.testing

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.core.transport.ExecResult
import io.github.joelkanyi.peek.core.transport.FileStat
import io.github.joelkanyi.peek.core.transport.TransportCapabilities
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * A scriptable [DeviceTransport] for headless tests: no device required. Feed it
 * devices, per-device processes, and a path-to-bytes file map. P1 extends it with
 * failure injection (not-debuggable, torn reads, device loss).
 */
public class FakeTransport(
    override val capabilities: TransportCapabilities = TransportCapabilities(canWrite = false, canPushEvents = false),
    private val devices: List<Device> = emptyList(),
    private val processes: Map<String, List<AppPackage>> = emptyMap(),
    private val files: Map<String, ByteString> = emptyMap(),
) : DeviceTransport {

    override suspend fun listDevices(): List<Device> = devices

    override suspend fun listDebuggableProcesses(device: Device): List<AppPackage> =
        processes[device.serial].orEmpty()

    override suspend fun exec(device: Device, command: String): ExecResult =
        ExecResult(exitCode = 0, stdout = ByteString.EMPTY, stderr = "")

    override suspend fun readFile(device: Device, pkg: AppPackage, path: String): ByteString =
        files[path] ?: throw NoSuchElementException("no fake file at $path")

    override suspend fun stat(device: Device, pkg: AppPackage, path: String): FileStat? =
        files[path]?.let { FileStat(mtimeEpochMs = 0L, sizeBytes = it.size.toLong()) }

    public companion object {
        /** Convenience for building a UTF-8 text file entry. */
        public fun textFile(contents: String): ByteString = contents.encodeUtf8()
    }
}
